package com.agentdid127.spawnercmd.via;

import com.agentdid127.spawnercmd.nbt.ClientItemEra;
import com.agentdid127.spawnercmd.nbt.ItemCmdConverter;
import com.agentdid127.spawnercmd.nbt.NbtCompound;
import com.agentdid127.spawnercmd.velocity.TsfServerGate;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockEntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.viaversion.viaversion.api.Via;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Velocity outgoing hook: rewrite mob-spawner preview item CMD for modern clients
 * <b>only</b> while the player is on a backend matching {@link TsfServerGate}.
 *
 * <p>Must call {@link PacketSendEvent#markForReEncode(boolean)} after mutating wrappers,
 * otherwise PacketEvents keeps the original buffer and the client never sees the rewrite.
 */
public final class SpawnerBlockEntityRewriter extends PacketListenerAbstract {
    private final Logger logger;
    private final boolean logConversions;
    private final TsfServerGate gate;

    public SpawnerBlockEntityRewriter(Logger logger, boolean logConversions, TsfServerGate gate) {
        // LOWEST: run after ViaVersion when possible so we rewrite the client-bound
        // packet Via already translated (Via does not upgrade CMD inside spawner BE).
        super(PacketListenerPriority.LOWEST);
        this.logger = logger;
        this.logConversions = logConversions;
        this.gate = gate;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) {
            return;
        }
        Player player = (Player) playerObj;
        UUID id = player.getUniqueId();

        if (!gate.isActive(id)) {
            gate.refresh(player);
            if (!gate.isActive(id)) {
                return;
            }
        }

        int protocol = protocolOf(player);
        ClientItemEra era = ClientItemEra.fromProtocol(protocol);
        if (era == ClientItemEra.LEGACY_TAG) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            handleBlockEntityData(event, player, protocol, era);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event, player, protocol, era);
        }
    }

    private void handleBlockEntityData(PacketSendEvent event, Player player, int protocol, ClientItemEra era) {
        WrapperPlayServerBlockEntityData packet = new WrapperPlayServerBlockEntityData(event);
        NBTCompound pe = packet.getNBT();
        if (!isMobSpawner(pe)) {
            return;
        }
        if (!rewriteInPlace(pe, protocol)) {
            return;
        }
        packet.setNBT(pe);
        event.markForReEncode(true);
        logConversion(player, protocol, era, "BLOCK_ENTITY_DATA", headItemSnippet(pe));
    }

    private void handleChunkData(PacketSendEvent event, Player player, int protocol, ClientItemEra era) {
        try {
            WrapperPlayServerChunkData chunk = new WrapperPlayServerChunkData(event);
            Column column = chunk.getColumn();
            if (column == null) {
                return;
            }
            TileEntity[] tiles = column.getTileEntities();
            if (tiles == null) {
                return;
            }
            int rewritten = 0;
            for (TileEntity tile : tiles) {
                if (tile == null) {
                    continue;
                }
                NBTCompound pe = tile.getNBT();
                if (!isMobSpawner(pe)) {
                    continue;
                }
                if (rewriteInPlace(pe, protocol)) {
                    rewritten++;
                }
            }
            if (rewritten == 0) {
                return;
            }
            // Ensure wrapper keeps the mutated column and re-encodes the buffer.
            chunk.setColumn(column);
            event.markForReEncode(true);
            String head = "?";
            for (TileEntity tile : tiles) {
                if (tile != null && isMobSpawner(tile.getNBT())) {
                    head = headItemSnippet(tile.getNBT());
                    break;
                }
            }
            logConversion(player, protocol, era, "CHUNK_DATA (" + rewritten + " spawner(s))", head);
        } catch (Throwable t) {
            logger.warn(
                    "[SpawnerCmdRewriter] CHUNK_DATA hook failed for {} (protocol {}): {}",
                    player.getUsername(),
                    protocol,
                    t.toString(),
                    t
            );
        }
    }

    private void logConversion(Player player, int protocol, ClientItemEra era, String where, String headSnippet) {
        if (!logConversions) {
            return;
        }
        String server = player.getCurrentServer()
                .map(c -> c.getServerInfo().getName())
                .orElse("?");
        logger.info(
                "[SpawnerCmdRewriter] converted spawner CMD for player={} server={} protocol={} era={} via={} head={} (re-encoded)",
                player.getUsername(),
                server,
                protocol,
                era,
                where,
                headSnippet
        );
    }

    private boolean rewriteInPlace(NBTCompound pe, int protocol) {
        NbtCompound internal = PeNbtAdapter.toInternal(pe);
        boolean changed = ItemCmdConverter.convertSpawnerBlockEntity(internal, protocol);
        if (!changed) {
            return false;
        }
        NBTCompound rewritten = PeNbtAdapter.toPacketEvents(internal);
        // PacketEvents getTags() is unmodifiable — must removeTag/setTag, not Map.clear().
        for (String key : new ArrayList<>(pe.getTagNames())) {
            pe.removeTag(key);
        }
        for (Map.Entry<String, NBT> e : rewritten.getTags().entrySet()) {
            pe.setTag(e.getKey(), e.getValue());
        }
        return true;
    }

    private static String headItemSnippet(NBTCompound spawnerPe) {
        try {
            NbtCompound spawner = PeNbtAdapter.toInternal(spawnerPe);
            NbtCompound spawnData = spawner.getCompound("SpawnData");
            if (spawnData == null) {
                return "?";
            }
            NbtCompound entity = spawnData.getCompound("entity");
            if (entity == null) {
                entity = spawnData;
            }
            NbtCompound equipment = entity.getCompound("equipment");
            if (equipment != null && equipment.getCompound("head") != null) {
                return equipment.getCompound("head").toString();
            }
            if (entity.getList("ArmorItems") == null || entity.getList("ArmorItems").size() < 4) {
                return "?";
            }
            return entity.getList("ArmorItems").get(3).toString();
        } catch (Exception e) {
            return "<err>";
        }
    }

    private static boolean isMobSpawner(NBTCompound nbt) {
        if (nbt == null) {
            return false;
        }
        NBTString idTag = nbt.getStringTagOrNull("id");
        if (idTag != null) {
            String id = idTag.getValue().toLowerCase(Locale.ROOT);
            if (id.equals("minecraft:mob_spawner")
                    || id.equals("mob_spawner")
                    || id.equals("minecraft:spawner")
                    || id.equals("spawner")) {
                return true;
            }
        }
        return nbt.getTagOrNull("SpawnData") != null || nbt.getTagOrNull("SpawnPotentials") != null;
    }

    static int protocolOf(Player player) {
        return Via.getAPI().getPlayerVersion(player.getUniqueId());
    }
}
