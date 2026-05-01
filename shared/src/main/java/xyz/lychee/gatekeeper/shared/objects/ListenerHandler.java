package xyz.lychee.gatekeeper.shared.objects;

import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.manager.ModuleManager;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.net.InetAddress;

public class ListenerHandler {
    public void handleDisconnect(InetAddress address, String name) {
        int addressData = AddressUtils.addressToInteger(address);
        for (AbstractModule ac : ModuleManager.INSTANCE.getLoadedChecks()) {
            ac.handleDisconnect(address, name, addressData);
        }
    }

    public void handlePostLogin(InetAddress address, String name) {
        int addressData = AddressUtils.addressToInteger(address);
        for (AbstractModule ac : ModuleManager.INSTANCE.getLoadedChecks()) {
            ac.handlePostLogin(address, name, addressData);
        }
    }

    public Object handlePreLogin(InetAddress address, String name) {
        int addressData = AddressUtils.addressToInteger(address);
        DataManager data = DataManager.INSTANCE;
        if (data.hasAccess(addressData, EnumAccess.WHITELIST) || data.hasAccess(name, EnumAccess.WHITELIST)) {
            return null;
        }

        for (AbstractModule check : ModuleManager.INSTANCE.getLoadedChecks()) {
            if (check.handlePreLogin(address, name, addressData)) {
                check.printCheck(address, name);
                return check.getKickMessage();
            }
        }

        if (data.isSaveAllPlayers()) {
            StoredPlayer player = new StoredPlayer(name, addressData);
            if (!data.getPlayers().contains(player)) {
                data.updatePlayer(player);
            }
        }
        return null;
    }
}
