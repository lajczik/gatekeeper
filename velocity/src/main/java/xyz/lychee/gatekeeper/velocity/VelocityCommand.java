package xyz.lychee.gatekeeper.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import xyz.lychee.gatekeeper.shared.command.CommandHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VelocityCommand implements SimpleCommand {
    private final VelocityMain gatekeeper;
    private final CommandHandler<Component> handler;

    public VelocityCommand(VelocityMain gatekeeper) {
        this.gatekeeper = gatekeeper;
        this.handler = new CommandHandler<>(this.gatekeeper);
    }

    @Override
    public void execute(Invocation invocation) {
        this.handler.execute(this.gatekeeper.commandPlayer(invocation.source()), invocation.arguments());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(
                this.handler.suggest(this.gatekeeper.commandPlayer(invocation.source()), invocation.arguments())
        );
    }
}
