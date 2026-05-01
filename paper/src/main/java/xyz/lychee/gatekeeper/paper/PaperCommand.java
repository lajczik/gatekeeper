package xyz.lychee.gatekeeper.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.command.CommandHandler;

import java.util.Collections;
import java.util.List;

public class PaperCommand extends Command {
    private final PaperMain gatekeeper;
    private final CommandHandler<Component> handler;

    public PaperCommand(PaperMain gatekeeper) {
        super("gatekeeper", "Gatekeeper command manager", "/gatekeeper <whitelist/blacklist/info/reload>", Collections.singletonList("gk"));
        this.gatekeeper = gatekeeper;
        this.handler = new CommandHandler<>(this.gatekeeper);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) {
        this.handler.execute(this.gatekeeper.commandPlayer(sender), args);
        return true;
    }

    @NotNull
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        return this.handler.suggest(this.gatekeeper.commandPlayer(sender), args);
    }
}
