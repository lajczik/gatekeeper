package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class IpFilterModule extends AbstractModule {
    private final Set<Integer> listedIps = new HashSet<>();
    private boolean list_mode;

    public IpFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "IpFilter");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return GeoipManager.INSTANCE.getDownloadedProxies().contains(connection.getAddressData())
                || this.listedIps.contains(connection.getAddressData()) == this.list_mode;
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
        this.listedIps.clear();

        for (String address : this.getConfig().getStringList("list")) {
            this.listedIps.add(AddressUtils.ipv4ToInt(address));
        }
        this.list_mode = this.getConfig().getBoolean("list_mode");

        return true;
    }
}
