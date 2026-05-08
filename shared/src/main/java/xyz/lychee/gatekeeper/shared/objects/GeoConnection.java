package xyz.lychee.gatekeeper.shared.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;

import java.net.InetAddress;

@Getter
@Setter
@AllArgsConstructor
public class GeoConnection {
    private final InetAddress address;
    private final String name;
    private final int addressData;
    private final String country;
    private final int asn;

    public GeoConnection(InetAddress address, int addressData, String name) {
        this.address = address;
        this.name = name;
        this.addressData = addressData;
        this.country = GeoipManager.INSTANCE.getCountryCode(this.addressData);
        this.asn = GeoipManager.INSTANCE.getAsnCode(this.addressData);
    }
}
