package xyz.lychee.gatekeeper.shared.command.subcommand;

import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.command.PermissibleCommand;
import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WhitelistCommand<T> extends PermissibleCommand<T> {

    public WhitelistCommand(Gatekeeper<T> gatekeeper) {
        super(gatekeeper, "gatekeeper.command.whitelist");
    }

    @Override
    protected void handleExecution(CommandPlayer<T> player, String[] args) {
        AbstractLang<T> lang = this.getGatekeeper().language();
        if (args.length < 2) {
            player.sendMessage(lang, "messages.whitelist.usage");
            return;
        }

        String target = args[1];
        boolean isAdd = args[0].equalsIgnoreCase("add");
        boolean isRemove = args[0].equalsIgnoreCase("remove");

        if (!isAdd && !isRemove) {
            player.sendMessage(lang, "messages.whitelist.usage");
            return;
        }

        byte targetAccess = DataManager.INSTANCE.resolveAccess(target);
        EnumAccess newAccess = isAdd ? EnumAccess.WHITELIST : EnumAccess.NULL;

        if (isAdd && EnumAccess.WHITELIST.isEquals(targetAccess)) {
            player.sendMessage(lang, "messages.whitelist.exists", target);
            return;
        }

        if (isRemove && !EnumAccess.WHITELIST.isEquals(targetAccess)) {
            player.sendMessage(lang, "messages.whitelist.missing", target);
            return;
        }

        player.applyChange(target, newAccess);
        if (isAdd) {
            player.sendMessage(lang, "messages.whitelist.added", target);
        } else {
            player.sendMessage(lang, "messages.whitelist.removed", target);
        }
    }

    @NotNull
    @Override
    protected List<String> handleSuggestion(CommandPlayer<T> player, String[] args) {
        return args.length == 1 ? Arrays.asList("add", "remove") : Collections.emptyList();
    }
}