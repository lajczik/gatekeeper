package xyz.lychee.gatekeeper.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import xyz.lychee.gatekeeper.shared.manager.UpdaterManager;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;

import java.util.concurrent.TimeUnit;

public class VelocityListeners extends ListenerHandler {
    private final VelocityMain plugin;

    public VelocityListeners(VelocityMain plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent e) {
        return EventTask.async(() -> {
            this.handlePostLogin(e.getPlayer().getRemoteAddress().getAddress(), e.getPlayer().getUsername());

            UpdaterManager updater = UpdaterManager.INSTANCE;
            if (e.getPlayer().hasPermission("gatekeeper.updater") && updater.getCompared() < 0 && updater.isUpdater()) {
                this.plugin.getProxy().getScheduler().buildTask(this.plugin, () -> {
                    this.plugin.commandPlayer(e.getPlayer())
                            .sendMessage(this.plugin.language(), "messages.updater", updater.getLatestVersion(), updater.getLatestVersion());
                }).delay(3, TimeUnit.SECONDS).schedule();
            }
        });
    }

    @Subscribe(priority = 999)
    public EventTask onPlayerPreLogin(PreLoginEvent e) {
        return EventTask.async(() -> {
            Object message = this.handlePreLogin(e.getConnection().getRemoteAddress().getAddress(), e.getUsername());

            if (message instanceof Component) {
                e.setResult(PreLoginEvent.PreLoginComponentResult.denied((Component) message));
            }
        });
    }

    @Subscribe
    public EventTask onDisconnect(DisconnectEvent e) {
        return EventTask.async(() ->
                this.handleDisconnect(e.getPlayer().getRemoteAddress().getAddress(), e.getPlayer().getUsername())
        );
    }
}
