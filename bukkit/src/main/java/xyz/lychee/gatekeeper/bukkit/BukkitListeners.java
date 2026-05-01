package xyz.lychee.gatekeeper.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;

import java.net.InetSocketAddress;

public class BukkitListeners extends ListenerHandler implements Listener {
    @EventHandler
    public void onPostLogin(PlayerJoinEvent e) {
        InetSocketAddress isa = e.getPlayer().getAddress();
        if (isa != null) {
            this.handlePostLogin(isa.getAddress(), e.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent e) {
        Object message = this.handlePreLogin(e.getAddress(), e.getName());
        if (message instanceof String) {
            e.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            e.setKickMessage((String) message);
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
