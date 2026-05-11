package xyz.lychee.gatekeeper.sponge;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.util.Ticks;
import xyz.lychee.gatekeeper.shared.manager.UpdaterManager;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;
import xyz.lychee.gatekeeper.shared.objects.Log4jFilter;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class SpongeListeners extends ListenerHandler {
    private final SpongeMain plugin;

    public SpongeListeners(SpongeMain plugin) {
        this.plugin = plugin;

        try {
            org.apache.logging.log4j.Logger rootLogger = LogManager.getRootLogger();
            if (!(rootLogger instanceof Logger)) {
                return;
            }

            Logger logger = (Logger) rootLogger;
            logger.addFilter(new Log4jFilter());
        } catch (Throwable ignored) {}
    }

    @Listener(order = Order.PRE)
    public void onPlayerPreLogin(ServerSideConnectionEvent.Auth e) {
        InetAddress address = e.connection().address().getAddress();
        String name = e.profile().name().orElse("");

        Object message = this.handlePreLogin(address, name);
        if (message instanceof String) {
            e.setCancelled(true);
            e.setMessage(LegacyComponentSerializer.legacyAmpersand().deserialize((String) message));
        }
    }

    @Listener
    public void onPostLogin(ServerSideConnectionEvent.Join e, @First ServerPlayer player) {
        InetSocketAddress isa = player.connection().address();
        this.handlePostLogin(isa.getAddress(), player.name());

        UpdaterManager updater = UpdaterManager.INSTANCE;
        if (player.hasPermission("gatekeeper.admin") && updater.getCompared() < 0 && updater.isUpdater()) {

            Task task = Task.builder()
                    .delay(Ticks.of(60L))
                    .execute(() ->
                            player.sendMessage(this.plugin.language().message("messages.updater", updater.getLatestVersion(), updater.getLatestVersion()))
                    )
                    .plugin(this.plugin.getContainer())
                    .build();

            Sponge.asyncScheduler().submit(task);
        }
    }

    // Odpowiednik PlayerQuitEvent
    @Listener
    public void onDisconnect(ServerSideConnectionEvent.Disconnect e, @First ServerPlayer player) {
        InetSocketAddress isa = player.connection().address();
        this.handleDisconnect(isa.getAddress(), player.name());
    }
}