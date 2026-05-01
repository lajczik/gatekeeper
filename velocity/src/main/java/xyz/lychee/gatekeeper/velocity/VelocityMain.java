package xyz.lychee.gatekeeper.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.*;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.ColoredLogger;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.logging.Level;

public class VelocityMain implements Gatekeeper<Component> {
    private final ProxyServer proxy;
    private final File dataDirectory;
    private final LegacyComponentSerializer serializer;
    private final AbstractLang<Component> language;
    private final Metrics.Factory metricsFactory;
    private final String version;
    private ColoredLogger logger;
    private Metrics metrics;

    @Inject
    public VelocityMain(ProxyServer proxy, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory, PluginContainer container) {
        this.proxy = proxy;
        this.metricsFactory = metricsFactory;
        this.dataDirectory = dataDirectory.toFile();
        this.serializer = LegacyComponentSerializer.builder()
                .character('&')
                .hexCharacter('#')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.language = new VelocityLang(this);
        this.version = container.getDescription().getVersion().orElse("1.0.0");
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.logger = new VelocityColoredLogger();
        try {
            this.metrics = this.metricsFactory.make(this, 27356);

            ConfigManager.INSTANCE.loadConfig(this);
            DataManager.INSTANCE.loadDatabase(this);
            ModuleManager.INSTANCE.loadChecks(this);
            GeoipManager.INSTANCE.loadDatabases(this);
            TaskManager.INSTANCE.loadTasks(this);

            this.language.loadLanguage();

            EventManager eventManager = this.proxy.getEventManager();
            eventManager.register(this, new VelocityListeners());

            CommandManager commandManager = this.proxy.getCommandManager();
            commandManager.register(commandManager.metaBuilder("gatekeeper").plugin(this).build(), new VelocityCommand(this));
        } catch (Exception e) {
            this.logger.log(Level.SEVERE, "Failed to load plugin", e);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (this.metrics != null) this.metrics.shutdown();

        DataManager.INSTANCE.close();
    }

    @Override
    public InputStream resource(String name) {
        return this.getClass().getClassLoader().getResourceAsStream(name);
    }

    @Override
    public File dataFolder() {
        return this.dataDirectory;
    }

    @Override
    public ColoredLogger logger() {
        return this.logger;
    }

    @Override
    public String version() {
        return this.version;
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
                if (this.getPlayer() instanceof CommandSource) {
                    return ((CommandSource) this.getPlayer()).hasPermission(permission);
                }
                return false;
            }

            @Override
            public void sendMessage(Component message) {
                if (this.getPlayer() instanceof CommandSource) {
                    ((CommandSource) this.getPlayer()).sendMessage(message);
                }
            }

            @Override
            public void applyChange(String target, EnumAccess newAccess) {
                BlacklistModule check = ModuleManager.INSTANCE.getCheck(BlacklistModule.class);
                if (!(check.getKickMessage() instanceof Component)) return;

                Component kickMessage = (Component) check.getKickMessage();

                try {
                    if (AddressUtils.isIpAddress(target)) {
                        InetAddress addr = InetAddress.getByAddress(AddressUtils.parseIp(target));
                        DataManager.INSTANCE.setAccess(AddressUtils.addressToInteger(addr), newAccess);
                        if (newAccess == EnumAccess.BLACKLIST) {
                            VelocityMain.this.proxy.getAllPlayers().stream()
                                    .filter(player -> player.getRemoteAddress().getAddress().equals(addr))
                                    .forEach(player -> player.disconnect(kickMessage));
                        }
                        return;
                    }
                } catch (Exception ignored) {}

                DataManager.INSTANCE.setAccess(target, newAccess);
                if (newAccess == EnumAccess.BLACKLIST) {
                    VelocityMain.this.proxy.getPlayer(target).ifPresent(player -> player.disconnect(kickMessage));
                }
            }
        };
    }

    public class VelocityLang extends AbstractLang<Component> {
        public VelocityLang(VelocityMain gatekeeper) {
            super(gatekeeper);
        }

        @Override
        public Component color(String text, boolean prefix) {
            Component deserialized = serializer.deserialize(text);
            if (prefix && ConfigManager.INSTANCE.getPrefix() instanceof ComponentLike) {
                return deserialized
                        .replaceText(TextReplacementConfig.builder()
                                .matchLiteral("%prefix%")
                                .replacement((ComponentLike) ConfigManager.INSTANCE.getPrefix())
                                .build()
                        );
            }
            return deserialized;
        }
    }
}