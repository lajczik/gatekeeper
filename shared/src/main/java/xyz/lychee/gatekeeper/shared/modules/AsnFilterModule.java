package xyz.lychee.gatekeeper.shared.modules;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;

import java.io.IOException;

public class AsnFilterModule extends AbstractModule {
    private final IntOpenHashSet listedAsn = new IntOpenHashSet();
    private boolean list_mode;

    public AsnFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AsnFilter");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return connection.getAsn() > 0
                && (GeoipManager.INSTANCE.getBlacklistedAsns().contains(connection.getAsn()) || this.listedAsn.contains(connection.getAsn()) == this.list_mode);
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
    public boolean load() throws IOException {
        this.listedAsn.clear();

        this.listedAsn.addAll(this.getConfig().getIntList("list"));
        this.list_mode = this.getConfig().getBoolean("list_mode");

        return true;
    }

    @Override
    public boolean unload() {
        return true;
    }
}
