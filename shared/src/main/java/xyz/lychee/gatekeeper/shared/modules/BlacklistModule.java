package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;

import java.net.InetAddress;

public class BlacklistModule extends AbstractModule {
    public BlacklistModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "Blacklist");
    }

    @Override
    public boolean handlePreLogin(InetAddress address, String name, int dataAddress) {
        DataManager dataManager = DataManager.INSTANCE;
        return dataManager.hasAccess(dataAddress, EnumAccess.BLACKLIST)
                || dataManager.hasAccess(name, EnumAccess.BLACKLIST);
    }

    @Override
    public boolean handlePostLogin(InetAddress address, String name, int dataAddress) {
        return false;
    }

    @Override
    public boolean handleDisconnect(InetAddress address, String name, int dataAddress) {
        return false;
    }

    @Override
    public boolean load() {
        return true;
    }
}
