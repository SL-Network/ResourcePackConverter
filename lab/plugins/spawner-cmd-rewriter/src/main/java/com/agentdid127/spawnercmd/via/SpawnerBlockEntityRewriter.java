package com.agentdid127.spawnercmd.via;

import com.agentdid127.spawnercmd.nbt.ClientItemEra;
import com.agentdid127.spawnercmd.nbt.ItemCmdConverter;
import com.agentdid127.spawnercmd.nbt.NbtCompound;
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
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Outgoing packet hook: rewrite mob-spawner block-entity item CMD for the receiving client's era.
 *
 * <p>Runs on PacketEvents send path. Client protocol comes from ViaVersion so we only reshape
 * NBT when the player is actually on 1.20.5+ (components) / 1.21.4+ (floats) / 1.21.5+
 * ({@code equipment} map). The Paper world keeps legacy {@code tag.CustomModelData}; only the
 * on-wire copy is changed.
 */
public final class SpawnerBlockEntityRewriter extends PacketListenerAbstract {
    private final Logger logger;
    private final boolean debug;

    public SpawnerBlockEntityRewriter(Logger logger, boolean debug) {
        super(PacketListenerPriority.LOWEST);
        this.logger = logger;
        this.debug = debug;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object playerObj = event.getPlayer();
        if (!(playerObj instanceof Player)) {
            return;
        }
        Player player = (Player) playerObj;
        int protocol = Via.getAPI().getPlayerVersion(player.getUniqueId());
        ClientItemEra era = ClientItemEra.fromProtocol(protocol);
        if (era == ClientItemEra.LEGACY_TAG) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_ENTITY_DATA) {
            handleBlockEntityData(event, protocol, era);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.CHUNK_DATA) {
            handleChunkData(event, protocol, era);
        }
    }

    private void handleBlockEntityData(PacketSendEvent event, int protocol, ClientItemEra era) {
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
        if (debug) {
            logger.info("[SpawnerCmdRewriter] BLOCK_ENTITY_DATA rewritten for era " + era
                    + " protocol " + protocol);
        }
    }

    private void handleChunkData(PacketSendEvent event, int protocol, ClientItemEra era) {
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
            chunk.setColumn(column);
            event.markForReEncode(true);
            if (debug) {
                logger.info("[SpawnerCmdRewriter] CHUNK_DATA rewritten " + rewritten
                        + " spawner(s) for era " + era + " protocol " + protocol);
            }
        } catch (Throwable t) {
            if (debug) {
                logger.warning("[SpawnerCmdRewriter] CHUNK_DATA hook skipped: " + t.getMessage());
            }
        }
    }

    private boolean rewriteInPlace(NBTCompound pe, int protocol) {
        NbtCompound internal = PeNbtAdapter.toInternal(pe);
        boolean changed = ItemCmdConverter.convertSpawnerBlockEntity(internal, protocol);
        if (!changed) {
            return false;
        }
        NBTCompound rewritten = PeNbtAdapter.toPacketEvents(internal);
        // PacketEvents getTags() is unmodifiable — must removeTag/setTag, not Map.clear().
        for (String key : new java.util.ArrayList<>(pe.getTagNames())) {
            pe.removeTag(key);
        }
        for (Map.Entry<String, NBT> e : rewritten.getTags().entrySet()) {
            pe.setTag(e.getKey(), e.getValue());
        }
        return true;
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
}
