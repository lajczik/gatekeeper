package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.GeoRange;
import xyz.lychee.gatekeeper.shared.util.BinaryGeoIPBuilder;
import xyz.lychee.gatekeeper.shared.util.BinaryGeoIPDatabase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Getter
public class GeoipManager implements Runnable {
    public static final GeoipManager INSTANCE = new GeoipManager();
    private final Map<Integer, String> countryCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> asnCache = new ConcurrentHashMap<>();
    private final BinaryGeoIPDatabase database = new BinaryGeoIPDatabase();
    private Gatekeeper<?> gatekeeper;
    private Set<GeoRange<String>> countryRangeCache = Collections.emptySet();
    private Set<GeoRange<Integer>> asnRangeCache = Collections.emptySet();

    public void loadDatabases(Gatekeeper<?> gatekeeper) {
        this.gatekeeper = gatekeeper;
        File dataFile = new File(gatekeeper.dataFolder(), "geodata.ldb");

        try {
            if (this.needUpdate(dataFile)) {
                this.gatekeeper.logger().info("Downloading GeoIP database...");

                File sourceCsv = new File(gatekeeper.dataFolder(), "temp_geodata.csv");
                BinaryGeoIPBuilder builder = new BinaryGeoIPBuilder();
                builder.downloadSource(sourceCsv);
                builder.buildDatabase(sourceCsv, dataFile);

                sourceCsv.delete();
            } else {
                this.gatekeeper.logger().info("Using existing GeoIP database: " + dataFile.getName());
            }

            this.database.load(dataFile);

            cacheAllRanges();

            this.gatekeeper.logger().info("GeoIP database has been loaded successfully!");
        } catch (IOException ex) {
            this.gatekeeper.logger().log(Level.SEVERE, "GeoIP files could not be loaded.", ex);
            clearCache();
        }
    }

    private boolean needUpdate(File dataFile) {
        if (!dataFile.exists()) {
            return true;
        }

        long fileAgeInMillis = System.currentTimeMillis() - dataFile.lastModified();
        long updateIntervalMillis = 11 * 60 * 60 * 1000;

        return fileAgeInMillis > updateIntervalMillis;
    }

    private void cacheAllRanges() {
        clearCache();

        try {
            this.countryRangeCache = this.database.getAllCountryRanges();
            this.asnRangeCache = this.database.getAllASNRanges();

            this.gatekeeper.logger().info("Loaded " + this.countryRangeCache.size() + " country and " + this.asnRangeCache.size() + " asn ranges!");
        } catch (Exception ex) {
            this.gatekeeper.logger().log(Level.WARNING, "Failed to load GeoIP ranges", ex);
            clearCache();
        }
    }

    public @NotNull String getCountryCode(int addressData) {
        String cached = countryCache.get(addressData);
        if (cached != null) {
            return cached;
        }

        for (GeoRange<String> record : countryRangeCache) {
            if (record.contains(addressData)) {
                countryCache.put(addressData, record.getValue());
                return record.getValue();
            }
        }
        String unknown = BinaryGeoIPDatabase.UNKNOWN_COUNTRY;
        countryCache.put(addressData, unknown);
        return unknown;
    }

    public @NotNull Integer getAsnCode(int addressData) {
        Integer cached = asnCache.get(addressData);
        if (cached != null) {
            return cached;
        }

        for (GeoRange<Integer> record : asnRangeCache) {
            if (record.contains(addressData)) {
                asnCache.put(addressData, record.getValue());
                return record.getValue();
            }
        }
        int unknown = BinaryGeoIPDatabase.UNKNOWN_ASN;
        asnCache.put(addressData, unknown);
        return unknown;
    }

    public void clearCache() {
        countryCache.clear();
        asnCache.clear();
    }

    @Override
    public void run() {
        if (this.gatekeeper != null) {
            this.loadDatabases(this.gatekeeper);
        }
    }
}