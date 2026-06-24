package xyz.lychee.gatekeeper.bungee;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import xyz.lychee.gatekeeper.shared.manager.UpdaterManager;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class BungeeListeners extends ListenerHandler implements Listener {
    private final BungeeMain plugin;

    public BungeeListeners(BungeeMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        this.handlePostLogin(this.getAddress(e.getPlayer()), e.getPlayer().getName());

        UpdaterManager updater = UpdaterManager.INSTANCE;
        if (e.getPlayer().hasPermission("gatekeeper.updater") && updater.getCompared() < 0 && updater.isUpdater()) {
            this.plugin.getProxy().getScheduler().schedule(this.plugin, () -> {
                        this.plugin.commandPlayer(e.getPlayer())
                                .sendMessage(this.plugin.language(), "messages.updater", updater.getLatestVersion(), updater.getLatestVersion());
                    },
                    3,
                    TimeUnit.SECONDS
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(PreLoginEvent e) {
        Object message = this.handlePreLogin(this.getAddress(e.getConnection()), e.getConnection().getName());
        if (message instanceof BaseComponent) {
            e.setCancelled(true);
            e.setReason((BaseComponent) message);
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent e) {
        this.handleDisconnect(this.getAddress(e.getPlayer()), e.getPlayer().getName());
    }

    public InetAddress getAddress(Connection conn) {
        return ((InetSocketAddress) conn.getSocketAddress()).getAddress();
    }
}
