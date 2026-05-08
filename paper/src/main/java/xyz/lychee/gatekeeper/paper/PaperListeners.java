package xyz.lychee.gatekeeper.paper;

import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.lychee.gatekeeper.shared.manager.UpdaterManager;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;
import xyz.lychee.gatekeeper.shared.objects.Log4jFilter;

import java.net.InetSocketAddress;

public class PaperListeners extends ListenerHandler implements Listener {
    private final PaperMain plugin;

    public PaperListeners(PaperMain plugin) {
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

    @EventHandler
    public void onPostLogin(PlayerJoinEvent e) {
        InetSocketAddress isa = e.getPlayer().getAddress();
        if (isa != null) {
            this.handlePostLogin(isa.getAddress(), e.getPlayer().getName());
        }

        UpdaterManager updater = UpdaterManager.INSTANCE;
        if (e.getPlayer().isOp() && updater.getCompared() < 0 && updater.isUpdater()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () ->
                    e.getPlayer().sendMessage(
                            this.plugin.language().message("messages.updater", updater.getLatestVersion(), updater.getLatestVersion())
                    ), 60L
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent e) {
        Object message = this.handlePreLogin(e.getAddress(), e.getName());
        if (message instanceof Component) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, (Component) message);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent e) {
        InetSocketAddress isa = e.getPlayer().getAddress();
        if (isa != null) {
            this.handleDisconnect(isa.getAddress(), e.getPlayer().getName());
        }
    }
}
