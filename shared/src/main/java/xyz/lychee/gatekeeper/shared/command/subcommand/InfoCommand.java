package xyz.lychee.gatekeeper.shared.command.subcommand;

import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.command.PermissibleCommand;
import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;

import java.util.Collections;
import java.util.List;

public class InfoCommand<T> extends PermissibleCommand<T> {

    public InfoCommand(Gatekeeper<T> gatekeeper) {
        super(gatekeeper, "gatekeeper.command.info");
    }

    @Override
    protected void handleExecution(CommandPlayer<T> player, String[] args) {
        AbstractLang<T> lang = this.getGatekeeper().language();
        if (args.length >= 1) {
            String target = args[0];
            EnumAccess targetAccess = EnumAccess.getByType(DataManager.INSTANCE.resolveAccess(target));

            if (targetAccess != EnumAccess.NULL) {
                player.sendMessage(lang, "messages.info.player", target, targetAccess.name());
                return;
            }
            player.sendMessage(lang, "messages.info.error", args[0]);
            return;
        }
        player.sendMessage(lang, "messages.info.usage");
    }

    @NotNull
    @Override
    protected List<String> handleSuggestion(CommandPlayer<T> player, String[] args) {
        return Collections.emptyList();
    }
}
