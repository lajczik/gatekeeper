package xyz.lychee.gatekeeper.shared.command;

import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.command.subcommand.BlacklistCommand;
import xyz.lychee.gatekeeper.shared.command.subcommand.InfoCommand;
import xyz.lychee.gatekeeper.shared.command.subcommand.ReloadCommand;
import xyz.lychee.gatekeeper.shared.command.subcommand.WhitelistCommand;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;

import java.util.*;

public class CommandHandler<T> extends PermissibleCommand<T> {
    private final Map<String, PermissibleCommand<T>> commandMap = new HashMap<>();

    public CommandHandler(Gatekeeper<T> gatekeeper) {
        super(gatekeeper, "gatekeeper.command");

        this.commandMap.put("whitelist", new WhitelistCommand<>(gatekeeper));
        this.commandMap.put("blacklist", new BlacklistCommand<>(gatekeeper));
        this.commandMap.put("info", new InfoCommand<>(gatekeeper));
        this.commandMap.put("reload", new ReloadCommand<>(gatekeeper));
    }

    @Override
    protected void handleExecution(CommandPlayer<T> player, String[] args) {
        AbstractLang<T> lang = this.getGatekeeper().language();

        if (args.length == 0 || !this.commandMap.containsKey(args[0])) {
            player.sendMessage(lang, "messages.main_usage");
            return;
        }

        this.commandMap.get(args[0]).execute(player, Arrays.copyOfRange(args, 1, args.length));
    }

    @NotNull
    @Override
    protected List<String> handleSuggestion(CommandPlayer<T> player, String[] args) {
        if (args.length == 1) {
            List<String> firstArguments = new ArrayList<>();
            for (String commandString : this.commandMap.keySet()) {
                if (this.commandMap.get(commandString) != null) {
                    PermissibleCommand<T> cmd = this.commandMap.get(commandString);
                    if (player.hasPermission(cmd.getPermission()))
                        firstArguments.add(commandString);
                } else {
                    firstArguments.add(commandString);
                }
            }
            return firstArguments;
        } else if (args.length > 1 && this.commandMap.containsKey(args[0])) {
            return this.commandMap.get(args[0]).suggest(player, Arrays.copyOfRange(args, 1, args.length));
        } else {
            return Collections.emptyList();
        }
    }
}
