package xyz.lychee.gatekeeper.sponge;

import net.kyori.adventure.text.Component;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.ArgumentReader;
import xyz.lychee.gatekeeper.shared.command.CommandHandler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SpongeCommand implements Command.Raw {
    private final SpongeMain gatekeeper;
    private final CommandHandler<Component> handler;

    public SpongeCommand(SpongeMain gatekeeper) {
        this.gatekeeper = gatekeeper;
        this.handler = new CommandHandler<>(this.gatekeeper);
    }

    @Override
    public CommandResult process(CommandCause cause, ArgumentReader.Mutable arguments) {
        String[] args = arguments.input().isEmpty() ? new String[0] : arguments.input().split(" ");
        this.handler.execute(this.gatekeeper.commandPlayer(cause), args);
        return CommandResult.success();
    }

    @Override
    public List<CommandCompletion> complete(CommandCause cause, ArgumentReader.Mutable arguments) {
        String[] args = arguments.input().isEmpty() ? new String[0] : arguments.input().split(" ");
        return this.handler.suggest(this.gatekeeper.commandPlayer(cause), args)
                .stream()
                .map(CommandCompletion::of)
                .collect(Collectors.toList());
    }

    @Override
    public boolean canExecute(CommandCause cause) {
        return true;
    }

    @Override
    public Optional<Component> shortDescription(CommandCause cause) {
        return Optional.empty();
    }

    @Override
    public Optional<Component> extendedDescription(CommandCause cause) {
        return Optional.empty();
    }

    @Override
    public Component usage(CommandCause cause) {
        return null;
    }
}
