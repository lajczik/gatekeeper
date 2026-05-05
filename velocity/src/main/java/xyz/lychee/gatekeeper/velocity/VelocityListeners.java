package xyz.lychee.gatekeeper.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import net.kyori.adventure.text.Component;
import xyz.lychee.gatekeeper.shared.objects.ListenerHandler;

public class VelocityListeners extends ListenerHandler {
    @Subscribe
    public EventTask onPostLogin(PostLoginEvent e) {
        return EventTask.async(() ->
                this.handlePostLogin(e.getPlayer().getRemoteAddress().getAddress(), e.getPlayer().getUsername())
        );
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
