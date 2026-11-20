package com.agentdid127.spawnercmd;

import com.agentdid127.spawnercmd.via.SpawnerBlockEntityRewriter;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper 1.16.4 plugin: rewrite mob-spawner preview item CustomModelData for ViaVersion clients.
 *
 * <p>Requires ViaVersion (protocol version) and packetevents (outgoing BE/chunk hooks) on the
 * same Paper server. Does not mutate world NBT — only the copy sent to each player.
 */
public final class SpawnerCmdRewriterPlugin extends JavaPlugin {
    private PacketListenerCommon listener;

    @Override
    public void onEnable() {
        boolean debug = getConfig().getBoolean("debug", false);
        saveDefaultConfig();
        listener = new SpawnerBlockEntityRewriter(getLogger(), debug);
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        getLogger().info("SpawnerCmdRewriter enabled (ViaVersion + packetevents). "
                + "Rewrites spawner preview CustomModelData for 1.20.5+ / 1.21.4+ clients.");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            listener = null;
        }
    }
}
