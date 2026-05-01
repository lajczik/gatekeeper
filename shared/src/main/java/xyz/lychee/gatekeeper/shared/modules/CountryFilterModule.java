package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;

import java.net.InetAddress;
import java.util.HashSet;

public class CountryFilterModule extends AbstractModule {
    private final HashSet<String> list = new HashSet<>();
    private boolean listMode;

    public CountryFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "CountryFilter");
    }

    @Override
    public boolean handlePreLogin(InetAddress address, String name, int dataAddress) {
        return this.listMode == this.list.contains(GeoipManager.INSTANCE.getCountryCode(dataAddress));
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
        this.listMode = this.getConfig().getBoolean("list_mode");

        this.list.clear();
        this.list.addAll(this.getConfig().getStringList("list"));

        return true;
    }
}
