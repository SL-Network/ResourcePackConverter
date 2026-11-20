package com.agentdid127.spawnercmd.velocity;

import com.viaversion.viaversion.api.Via;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether each player is currently on a backend whose registered name
 * starts with the configured prefix (default {@code terra_swoop_force}).
 */
public final class TsfServerGate {
    private final String prefixLower;
    private final Map<UUID, Boolean> active = new ConcurrentHashMap<>();
    private final Logger logger;
    private final boolean logConversions;

    public TsfServerGate(String serverNamePrefix, Logger logger, boolean logConversions) {
        this.prefixLower = serverNamePrefix == null
                ? "terra_swoop_force"
                : serverNamePrefix.toLowerCase(Locale.ROOT);
        this.logger = logger;
        this.logConversions = logConversions;
    }

    public String prefix() {
        return prefixLower;
    }

    public boolean isActive(UUID playerId) {
        Boolean v = active.get(playerId);
        return v != null && v;
    }

    public boolean matchesServerName(String serverName) {
        if (serverName == null) {
            return false;
        }
        return serverName.toLowerCase(Locale.ROOT).startsWith(prefixLower);
    }

    public void refresh(Player player) {
        Optional<ServerConnection> conn = player.getCurrentServer();
        if (conn.isEmpty()) {
            active.put(player.getUniqueId(), Boolean.FALSE);
            return;
        }
        String name = conn.get().getServerInfo().getName();
        active.put(player.getUniqueId(), matchesServerName(name));
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String name = event.getServer().getServerInfo().getName();
        boolean match = matchesServerName(name);
        active.put(event.getPlayer().getUniqueId(), match);
        if (logConversions) {
            Player player = event.getPlayer();
            int protocol = Via.getAPI().getPlayerVersion(player.getUniqueId());
            if (match) {
                logger.info(
                        "[SpawnerCmdRewriter] TSF gate ON for player={} server={} protocol={} (spawner CMD rewrite armed)",
                        player.getUsername(),
                        name,
                        protocol
                );
            } else {
                logger.info(
                        "[SpawnerCmdRewriter] TSF gate OFF for player={} server={} (prefix '{}')",
                        player.getUsername(),
                        name,
                        prefixLower
                );
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }
}
