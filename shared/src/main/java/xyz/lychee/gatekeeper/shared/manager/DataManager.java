package xyz.lychee.gatekeeper.shared.manager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class DataManager extends AbstractManager implements Runnable {
    public static final DataManager INSTANCE = new DataManager();

    private final Map<Integer, Byte> addresses = new ConcurrentHashMap<>();
    private final Map<String, Byte> nicknames = new ConcurrentHashMap<>();

    private Logger logger;
    private File dataFile;

    @Override
    public boolean load(Gatekeeper<?> gatekeeper) throws IOException, JsonParserException {
        this.logger = gatekeeper.logger();
        this.dataFile = new File(gatekeeper.dataFolder(), "data.json");

        if (!this.dataFile.exists()) {
            this.dataFile.getParentFile().mkdirs();
            this.dataFile.createNewFile();
        }

        this.loadFromFile();
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    private void loadFromFile() throws IOException, JsonParserException {
        try (FileReader reader = new FileReader(this.dataFile)) {
            if (this.dataFile.length() == 0) return;

            JsonObject json = JsonParser.object().from(reader);

            JsonObject addressesObj = json.getObject("addresses");
            if (addressesObj != null) {
                for (Map.Entry<String, Object> entry : addressesObj.entrySet()) {
                    try {
                        this.addresses.put(Integer.parseInt(entry.getKey()), ((Number) entry.getValue()).byteValue());
                    } catch (NumberFormatException ignored) {}
                }
            }

            JsonObject nicknamesObj = json.getObject("nicknames");
            if (nicknamesObj != null) {
                for (Map.Entry<String, Object> entry : nicknamesObj.entrySet()) {
                    this.nicknames.put(entry.getKey(), ((Number) entry.getValue()).byteValue());
                }
            }
        }
    }

    public boolean updateAndCheckAccess(GeoConnection connection, EnumAccess targetAccess) {
        Byte access = this.nicknames.get(connection.getName());
        if (access == null) {
            access = this.addresses.get(connection.getAddressData());
        }
        if (access != null) {
            connection.setAccess(EnumAccess.getByType(access));
        }
        return access != null && access == targetAccess.getType();
    }

    public void setAccess(int address, EnumAccess access) {
        this.addresses.put(address, access.getType());
    }

    public void setAccess(String nickname, EnumAccess access) {
        this.nicknames.put(nickname, access.getType());
    }

    public void updateAccess(int ip, EnumAccess access) {
        this.setAccess(ip, access);
    }

    public void updateAccess(String nickname, EnumAccess access) {
        this.setAccess(nickname, access);
    }

    public byte resolveAccess(String target) {
        try {
            if (AddressUtils.isIpv4(target)) {
                int addressData = AddressUtils.ipv4ToInt(target);
                return this.addresses.getOrDefault(addressData, (byte) 0);
            }
        } catch (Exception ignored) {}

        return this.nicknames.getOrDefault(target, (byte) 0);
    }

    @Override
    public void run() {
        JsonObject json = new JsonObject();
        json.put("addresses", this.addresses);
        json.put("nicknames", this.nicknames);

        try {
            Files.writeString(this.dataFile.toPath(), JsonWriter.string(json));
        } catch (IOException ex) {
            this.logger.log(Level.SEVERE, "Nie udało się zapisać pliku data.json", ex);
        }
    }
}