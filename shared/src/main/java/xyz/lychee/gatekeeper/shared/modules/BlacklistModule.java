package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;

public class BlacklistModule extends AbstractModule {
    public BlacklistModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "Blacklist");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return connection.getAccess() == EnumAccess.BLACKLIST;
    }

    @Override
    public boolean handlePostLogin(GeoConnection connection) {
        return false;
    }

    @Override
    public boolean handleDisconnect(GeoConnection connection) {
        return false;
    }

    @Override
    public boolean load() {
        return true;
    }

    @Override
    public boolean unload() {
        return true;
    }
}
