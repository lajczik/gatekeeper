package xyz.lychee.gatekeeper.shared.objects;

import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.manager.ModuleManager;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ListenerHandler {
    private final Map<Integer, GeoConnection> connections = new ConcurrentHashMap<>();

    public void handleDisconnect(InetAddress address, String name) {
        int addressData = AddressUtils.ipv4ToInt(address);
        GeoConnection connection = this.connections.computeIfAbsent(addressData, k -> new GeoConnection(address, addressData, name));
        for (AbstractModule ac : ModuleManager.INSTANCE.getLoadedChecks()) {
            ac.handleDisconnect(connection);
        }
    }

    public void handlePostLogin(InetAddress address, String name) {
        int addressData = AddressUtils.ipv4ToInt(address);
        GeoConnection connection = this.connections.computeIfAbsent(addressData, k -> new GeoConnection(address, addressData, name));
        for (AbstractModule ac : ModuleManager.INSTANCE.getLoadedChecks()) {
            ac.handlePostLogin(connection);
        }
    }

    public Object handlePreLogin(InetAddress address, String name) {
        int addressData = AddressUtils.ipv4ToInt(address);
        GeoConnection connection = this.connections.computeIfAbsent(addressData, k -> new GeoConnection(address, addressData, name));
        connection.setTimestamp(System.currentTimeMillis());
        if (DataManager.INSTANCE.updateAndCheckAccess(connection, EnumAccess.WHITELIST)) {
            return null;
        }

        for (AbstractModule check : ModuleManager.INSTANCE.getLoadedChecks()) {
            if (check.handlePreLogin(connection)) {
                check.printCheck(connection);
                return check.getKickMessage();
            }
        }
        return null;
    }
}
