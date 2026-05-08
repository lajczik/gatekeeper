package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.GeoRange;
import xyz.lychee.gatekeeper.shared.objects.BinaryGeoIPBuilder;
import xyz.lychee.gatekeeper.shared.objects.BinaryGeoIPDatabase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Getter
public class GeoipManager implements Runnable {
    public static final GeoipManager INSTANCE = new GeoipManager();
    private final Map<Integer, String> countryCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> asnCache = new ConcurrentHashMap<>();
    private final BinaryGeoIPDatabase database = new BinaryGeoIPDatabase();
    private Gatekeeper<?> gatekeeper;
    private List<GeoRange<String>> countryRangeCache = Collections.emptyList();
    private List<GeoRange<Integer>> asnRangeCache = Collections.emptyList();

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
        return this.getFromCacheOrSearch(addressData, countryCache, countryRangeCache, BinaryGeoIPDatabase.UNKNOWN_COUNTRY);
    }

    public @NotNull Integer getAsnCode(int addressData) {
        return this.getFromCacheOrSearch(addressData, asnCache, asnRangeCache, BinaryGeoIPDatabase.UNKNOWN_ASN);
    }

    private <V> V getFromCacheOrSearch(int ip, Map<Integer, V> cache, List<GeoRange<V>> ranges, V defaultValue) {
        V cached = cache.get(ip);
        if (cached != null) {
            return cached;
        }

        int low = 0;
        int high = ranges.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            GeoRange<V> midVal = ranges.get(mid);

            if (Integer.compareUnsigned(ip, midVal.getStart()) < 0) {
                high = mid - 1;
            } else if (Integer.compareUnsigned(ip, midVal.getEnd()) > 0) {
                low = mid + 1;
            } else {
                V foundValue = midVal.getValue();
                cache.put(ip, foundValue);
                return foundValue;
            }
        }

        cache.put(ip, defaultValue);
        return defaultValue;
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