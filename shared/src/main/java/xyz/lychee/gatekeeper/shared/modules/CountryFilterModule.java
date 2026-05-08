package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;

import java.util.HashSet;

public class CountryFilterModule extends AbstractModule {
    private final HashSet<String> list = new HashSet<>();
    private boolean listMode;

    public CountryFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "CountryFilter");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return this.list.contains(connection.getCountry()) == this.listMode;
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
        this.listMode = this.getConfig().getBoolean("list_mode");

        this.list.clear();
        this.list.addAll(this.getConfig().getStringList("list"));

        return true;
    }
}
