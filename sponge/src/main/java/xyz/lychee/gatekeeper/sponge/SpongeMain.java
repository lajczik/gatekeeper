package xyz.lychee.gatekeeper.sponge;

import com.google.inject.Inject;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StartingEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.*;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.*;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

@Getter
@Plugin("gatekeeper")
public class SpongeMain implements Gatekeeper<Component> {
    private final PluginContainer container;
    private final Path dataDirectory;
    private final Game game;
    private final AbstractLang<Component> language;
    private final ColoredLogger logger;
    private final LegacyComponentSerializer serializer;
    private PlatformData platformData;

    @Inject
    public SpongeMain(PluginContainer pluginContainer, Logger logger, Game game, @ConfigDir(sharedRoot = false) Path dataDirectory) {
        this.container = pluginContainer;
        this.dataDirectory = dataDirectory;
        this.game = game;
        this.logger = new SpongeColoredLogger(logger);
        this.serializer = LegacyComponentSerializer.builder()
                .character('&')
                .hexCharacter('#')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.language = new SpongeLang(this);
    }

    @Listener
    public void onEnable(StartedEngineEvent<Server> event) {
        this.platformData = new PlatformData(
                this.container.metadata().version().toString(),
                this.game.platform().container(Platform.Component.IMPLEMENTATION).metadata().id(),
                this.game.platform().minecraftVersion().name(),
                this.game.server().isOnlineModeEnabled()
        );

        this.logger.sendHeader(this);
        this.loadManagers();
        this.language.loadLanguage();

        Sponge.eventManager().registerListeners(this.container, new SpongeListeners(this));
    }

    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Raw> event) {
        event.register(this.container, new SpongeCommand(this), "gatekeeper");
    }

    @Listener
    public void onServerStopping(StoppingEngineEvent<Server> event) {
        this.unloadManagers();
    }

    @Override
    public InputStream resource(String name) {
        return this.container.openResource(name).orElse(null);
    }

    @Override
    public File dataFolder() {
        return this.dataDirectory.toFile();
    }

    @Override
    public ColoredLogger logger() {
        return this.logger;
    }

    @Override
    public PlatformData platformData() {
        this.platformData.setPlayers(this.game.server().onlinePlayers().size());
        return this.platformData;
    }

    @Override
    public AbstractLang<Component> language() {
        return this.language;
    }

    @Override
    public CommandPlayer<Component> commandPlayer(Object player) {
        return new CommandPlayer<Component>(player) {
            @Override
            public boolean hasPermission(String permission) {
                if (this.getPlayer() instanceof CommandCause) {
                    return ((CommandCause) this.getPlayer()).hasPermission(permission);
                }
                return false;
            }

            @Override
            public void sendMessage(Component message) {
                if (this.getPlayer() instanceof CommandCause) {
                    ((CommandCause) this.getPlayer()).audience().sendMessage(message);
                }
            }

            @Override
            public void applyChange(String target, EnumAccess newAccess) {
                BlacklistModule check = ModuleManager.INSTANCE.getCheck(BlacklistModule.class);
                Object kickMessage = check.getKickMessage();

                if (AddressUtils.isIpv4(target)) {
                    int addressData = AddressUtils.ipv4ToInt(target);
                    DataManager.INSTANCE.setAccess(addressData, newAccess);
                    if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof String) {
                        Component kickComp = LegacyComponentSerializer.legacyAmpersand().deserialize((String) kickMessage);
                        Sponge.server().onlinePlayers().stream()
                                .filter(p -> AddressUtils.isIpv4Equal(p.connection().address().getAddress(), addressData))
                                .forEach(p -> p.kick(kickComp));
                    }
                    return;
                }

                DataManager.INSTANCE.setAccess(target, newAccess);
                if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof String) {
                    Component kickComp = LegacyComponentSerializer.legacyAmpersand().deserialize((String) kickMessage);
                    Sponge.server().player(target).ifPresent(p -> p.kick(kickComp));
                }
            }
        };
    }

    public class SpongeLang extends AbstractLang<Component> {
        public SpongeLang(SpongeMain gatekeeper) {
            super(gatekeeper);
        }

        @Override
        public Component color(String text, boolean prefix) {
            Component deserialized = serializer.deserialize(text);
            if (prefix && ConfigManager.INSTANCE.getPrefix() instanceof ComponentLike) {
                return deserialized
                        .replaceText(TextReplacementConfig.builder()
                                .matchLiteral("%prefix%")
                                .replacement((Component) ConfigManager.INSTANCE.getPrefix())
                                .build()
                        );
            }
            return deserialized;
        }

        @Override
        public Component hover(String text, String hoverText) {
            return this.color(text, false).hoverEvent(HoverEvent.showText(this.color(hoverText, false)));
        }
    }
}