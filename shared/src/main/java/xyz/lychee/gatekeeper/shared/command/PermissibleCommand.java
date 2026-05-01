package xyz.lychee.gatekeeper.shared.command;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;

import java.util.Collections;
import java.util.List;

@Getter
public abstract class PermissibleCommand<T> {
    private final Gatekeeper<T> gatekeeper;
    private final String permission;

    public PermissibleCommand(Gatekeeper<T> gatekeeper, String permission) {
        this.gatekeeper = gatekeeper;
        this.permission = permission;
    }

    public void execute(CommandPlayer<T> player, String[] args) {
        if (!player.hasPermission(this.permission)) {
            player.sendMessage(this.gatekeeper.language(), "messages.no_permission");
            return;
        }
        this.handleExecution(player, args);
    }

    public List<String> suggest(CommandPlayer<T> player, String[] args) {
        return !player.hasPermission(this.permission)
                ? Collections.emptyList() : this.handleSuggestion(player, args);
    }

    protected abstract void handleExecution(CommandPlayer<T> player, String[] args);

    protected abstract List<String> handleSuggestion(CommandPlayer<T> player, String[] args);
}
