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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.ConfigManager;
import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.manager.ModuleManager;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.*;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;

@Getter
public class VelocityMain implements Gatekeeper<Component> {
    private final ProxyServer proxy;
    private final File dataDirectory;
    private final LegacyComponentSerializer serializer;
    private final AbstractLang<Component> language;
    private final ColoredLogger logger;
    private final PlatformData platformData;

    @Inject
    public VelocityMain(ProxyServer proxy, @DataDirectory Path dataDirectory, PluginContainer container) {
        this.proxy = proxy;
        this.dataDirectory = dataDirectory.toFile();
        this.serializer = LegacyComponentSerializer.builder()
                .character('&')
                .hexCharacter('#')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.language = new VelocityLang(this);
        this.logger = new VelocityColoredLogger();
        this.platformData = new PlatformData(
                container.getDescription().getVersion().orElse("1.0.0"),
                this.getProxy().getVersion().getVersion(),
                this.getProxy().getVersion().getName(),
                this.getProxy().getConfiguration().isOnlineMode()
        );
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.logger.sendHeader(this);

        this.loadManagers();

        this.language.loadLanguage();

        EventManager eventManager = this.proxy.getEventManager();
        eventManager.register(this, new VelocityListeners(this));

        CommandManager commandManager = this.proxy.getCommandManager();
        commandManager.register(commandManager.metaBuilder("gatekeeper").plugin(this).build(), new VelocityCommand(this));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        this.unloadManagers();
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
    public PlatformData platformData() {
        this.platformData.setPlayers(this.proxy.getPlayerCount());
        return this.platformData;
    }

    @Override
    public AbstractLang<Component> language() {
        return this.language;
    }

    @Override
    public CommandPlayer<Component> commandPlayer(Object player) {
        return new CommandPlayer<>(player) {
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
                byte accessType = newAccess.getType();
                Player targetPlayer = getProxy().getPlayer(target).orElse(null);

                if (AddressUtils.isIpv4(target)) {
                    int addressData = AddressUtils.ipv4ToInt(target);
                    DataManager.INSTANCE.getAddresses().put(addressData, accessType);
                    if (newAccess == EnumAccess.BLACKLIST) {
                        getProxy().getAllPlayers().stream()
                                .filter(player -> AddressUtils.isIpv4Equal(player.getRemoteAddress().getAddress(), addressData))
                                .forEach(player -> player.disconnect(kickMessage));
                    }
                } else if (RandomUtils.isInteger(target) && targetPlayer == null) {
                    int asn = Integer.parseInt(target);
                    DataManager.INSTANCE.getAsns().put(asn, accessType);
                } else {
                    DataManager.INSTANCE.getNicknames().put(target, accessType);
                    if (newAccess == EnumAccess.BLACKLIST && targetPlayer != null) {
                        targetPlayer.disconnect(kickMessage);
                    }
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

        @Override
        public Component hover(String text, String hoverText) {
            return this.color(text, false).hoverEvent(HoverEvent.showText(this.color(hoverText, false)));
        }
    }
}