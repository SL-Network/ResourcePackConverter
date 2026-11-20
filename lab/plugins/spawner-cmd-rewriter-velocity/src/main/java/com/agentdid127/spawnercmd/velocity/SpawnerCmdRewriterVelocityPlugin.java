package com.agentdid127.spawnercmd.velocity;

import com.agentdid127.spawnercmd.via.SpawnerBlockEntityRewriter;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Velocity copy of SpawnerCmdRewriter for proxy-side ViaVersion.
 *
 * <p>Conversions run only while the player is on a registered server whose name starts with
 * {@code terra_swoop_force} (configurable). Set {@code log-conversions=true} to see each rewrite.
 */
@Plugin(
        id = "spawner-cmd-rewriter",
        name = "SpawnerCmdRewriter",
        version = "1.0.0-SNAPSHOT",
        description = "Rewrites mob-spawner preview CustomModelData on TSF backends for modern clients",
        authors = {"agentdid127"},
        dependencies = {
                @Dependency(id = "viaversion"),
                @Dependency(id = "packetevents")
        }
)
public final class SpawnerCmdRewriterVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private PacketListenerCommon packetListener;

    @Inject
    public SpawnerCmdRewriterVelocityPlugin(
            ProxyServer proxy,
            Logger logger,
            @DataDirectory Path dataDirectory
    ) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        Properties props = loadConfig();
        String prefix = props.getProperty("server-name-prefix", "terra_swoop_force");
        // Prefer log-conversions; fall back to legacy debug=true
        boolean logConversions = Boolean.parseBoolean(
                props.getProperty(
                        "log-conversions",
                        props.getProperty("debug", "true")
                )
        );

        TsfServerGate gate = new TsfServerGate(prefix, logger, logConversions);
        proxy.getEventManager().register(this, gate);

        packetListener = new SpawnerBlockEntityRewriter(logger, logConversions, gate);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        logger.info(
                "SpawnerCmdRewriter (Velocity) enabled — prefix='{}' log-conversions={}",
                gate.prefix(),
                logConversions
        );
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.properties");
            if (!Files.exists(file)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                    if (in != null) {
                        Files.copy(in, file);
                    } else {
                        try (OutputStream out = Files.newOutputStream(file)) {
                            props.setProperty("server-name-prefix", "terra_swoop_force");
                            props.setProperty("log-conversions", "true");
                            props.store(out, "SpawnerCmdRewriter Velocity");
                        }
                    }
                }
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        } catch (IOException e) {
            logger.warn("Failed to load config; using defaults: {}", e.getMessage());
            props.setProperty("server-name-prefix", "terra_swoop_force");
            props.setProperty("log-conversions", "true");
        }
        return props;
    }
}
