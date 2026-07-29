package xyz.lychee.gatekeeper.shared.manager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteMaps;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMaps;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.MathUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class DataManager extends AbstractManager implements Runnable {
    public static final DataManager INSTANCE = new DataManager();

    private final Int2ByteMap addresses = Int2ByteMaps.synchronize(new Int2ByteOpenHashMap());
    private final Int2ByteMap asns = Int2ByteMaps.synchronize(new Int2ByteOpenHashMap());
    private final Object2ByteMap<String> nicknames = Object2ByteMaps.synchronize(new Object2ByteOpenHashMap<>());

    private Logger logger;
    private Path dataPath;

    public DataManager() {
        this.nicknames.defaultReturnValue((byte) 0);
        this.addresses.defaultReturnValue((byte) 0);
        this.asns.defaultReturnValue((byte) 0);
    }

    @Override
    public boolean load(Gatekeeper<?> plugin) throws IOException, JsonParserException {
        this.logger = plugin.logger();
        this.dataPath = new File(plugin.dataFolder(), "data.json").toPath();

        if (Files.notExists(this.dataPath)) {
            Files.createDirectories(this.dataPath.getParent());
        } else {
            this.loadDataFile();
        }
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> plugin) throws IOException, JsonParserException {
        this.addresses.clear();
        this.nicknames.clear();
        this.asns.clear();

        if (Files.notExists(this.dataPath)) {
            this.loadDataFile();
        }
        return true;
    }

    private void loadDataFile() throws IOException, JsonParserException {
        try (InputStream is = Files.newInputStream(this.dataPath)) {
            JsonObject json = JsonParser.object().from(is);

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

            JsonObject asnsObj = json.getObject("asns");
            if (asnsObj != null) {
                for (Map.Entry<String, Object> entry : asnsObj.entrySet()) {
                    this.asns.put(Integer.parseInt(entry.getKey()), ((Number) entry.getValue()).byteValue());
                }
            }
        }
    }

    public void saveDataFile() {
        JsonObject json = new JsonObject();
        json.put("addresses", this.addresses);
        json.put("asns", this.asns);
        json.put("nicknames", this.nicknames);

        try {
            Files.writeString(this.dataPath, JsonWriter.string(json));
        } catch (IOException ex) {
            this.logger.log(Level.SEVERE, "Failed to save database file " + this.dataPath.getFileName().toString(), ex);
        }
    }

    public void updateAddress(int addressData, byte accessType) {
        this.addresses.put(addressData, accessType);
    }

    public void updateAsn(int asn, byte accessType) {
        this.asns.put(asn, accessType);
    }

    public void updateNickname(String nickname, byte accessType) {
        this.nicknames.put(nickname, accessType);
    }

    public boolean updateAndCheckAccess(GeoConnection connection, EnumAccess targetAccess) {
        byte access = nicknames.getByte(connection.getName());
        if (access == 0) {
            access = addresses.get(connection.getAddressData());
        }
        if (access == 0) {
            access = asns.get(connection.getAsn());
        }
        if (access != 0) {
            connection.setAccess(EnumAccess.getByType(access));
            return access == targetAccess.getType();
        }
        return false;
    }

    public byte resolveAccess(String target) {
        if (AddressUtils.isIpv4(target)) {
            int addressData = AddressUtils.ipv4ToInt(target);
            return this.addresses.getOrDefault(addressData, (byte) 0);
        } else if (MathUtils.isInteger(target)) {
            int asn = Integer.parseInt(target);
            return this.asns.getOrDefault(asn, (byte) 0);
        } else {
            return this.nicknames.getOrDefault(target, (byte) 0);
        }
    }

    @Override
    public void run() {
        this.saveDataFile();
    }
}