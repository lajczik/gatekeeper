package xyz.lychee.gatekeeper.bukkit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import xyz.lychee.gatekeeper.shared.command.CommandHandler;

import java.util.List;

public class BukkitCommand implements TabExecutor, CommandExecutor {
    private final BukkitMain gatekeeper;
    private final CommandHandler<String> handler;

    public BukkitCommand(BukkitMain gatekeeper) {
        this.gatekeeper = gatekeeper;
        this.handler = new CommandHandler<>(this.gatekeeper);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        this.handler.execute(this.gatekeeper.commandPlayer(sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        return this.handler.suggest(this.gatekeeper.commandPlayer(sender), args);
    }
}
