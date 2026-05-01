package xyz.lychee.gatekeeper.shared.command.subcommand;

import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.command.PermissibleCommand;
import xyz.lychee.gatekeeper.shared.manager.ConfigManager;
import xyz.lychee.gatekeeper.shared.manager.ModuleManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;

import java.util.Collections;
import java.util.List;

public class ReloadCommand<T> extends PermissibleCommand<T> {

    public ReloadCommand(Gatekeeper<T> gatekeeper) {
        super(gatekeeper, "gatekeeper.command.reload");
    }

    @Override
    protected void handleExecution(CommandPlayer<T> player, String[] args) {
        AbstractLang<T> lang = this.getGatekeeper().language();

        ConfigManager.INSTANCE.loadConfig(this.getGatekeeper());
        ModuleManager.INSTANCE.reload();
        player.sendMessage(lang, "messages.reload.config");

        lang.loadLanguage();
        player.sendMessage(lang, "messages.reload.lang");
    }

    @NotNull
    @Override
    protected List<String> handleSuggestion(CommandPlayer<T> player, String[] args) {
        return Collections.emptyList();
    }
}
