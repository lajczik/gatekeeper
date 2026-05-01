package xyz.lychee.gatekeeper.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import xyz.lychee.gatekeeper.shared.command.CommandHandler;

public class BungeeCommand extends Command implements TabExecutor {
    private final BungeeMain gatekeeper;
    private final CommandHandler<BaseComponent> handler;

    public BungeeCommand(BungeeMain gatekeeper) {
        super("gatekeeper", null, "gk");
        this.gatekeeper = gatekeeper;
        this.handler = new CommandHandler<>(this.gatekeeper);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        this.handler.execute(this.gatekeeper.commandPlayer(sender), args);
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return this.handler.suggest(this.gatekeeper.commandPlayer(sender), args);
    }
}
