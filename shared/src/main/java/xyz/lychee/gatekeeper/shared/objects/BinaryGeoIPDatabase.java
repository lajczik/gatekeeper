package xyz.lychee.gatekeeper.shared.objects;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class BinaryGeoIPDatabase {
    public static final int UNKNOWN_ASN = -1;
    public static final String UNKNOWN_COUNTRY = "--";

    private volatile List<GeoRange<String>> countryRangeCache = Collections.emptyList();
    private volatile List<GeoRange<Integer>> asnRangeCache = Collections.emptyList();
    private ByteBuffer countryBuffer;
    private ByteBuffer asnBuffer;
    private int countryRecordCount;
    private int asnRecordCount;

    public void load(Path databaseFile) throws IOException {
        if (Files.notExists(databaseFile)) {
            throw new FileNotFoundException("Database file not found: " + databaseFile.getFileName().toString());
        }

        byte[] data = Files.readAllBytes(databaseFile);

        ByteBuffer buffer = ByteBuffer.wrap(data);

        int magic = buffer.getInt();
        if (magic != BinaryGeoIPBuilder.MAGIC_NUMBER) {
            throw new IOException("Invalid database format (wrong magic number)");
        }

        int version = buffer.getInt();
        if (version != BinaryGeoIPBuilder.VERSION) {
            throw new IOException("Unsupported database version: " + version);
        }

        countryRecordCount = buffer.getInt();
        asnRecordCount = buffer.getInt();

        int countryDataSize = countryRecordCount * 10; // 4 + 4 + 2 bytes per record
        byte[] countryData = new byte[countryDataSize];
        buffer.get(countryData);

        int asnDataSize = asnRecordCount * 12; // 4 + 4 + 4 bytes per record
        byte[] asnData = new byte[asnDataSize];
        buffer.get(asnData);

        countryBuffer = ByteBuffer.wrap(countryData).asReadOnlyBuffer();
        asnBuffer = ByteBuffer.wrap(asnData).asReadOnlyBuffer();

        this.countryRangeCache = this.readAllCountryRanges();
        this.asnRangeCache = this.readAllASNRanges();
    }

    private GeoRange<String> readCountryRecord(int index) {
        int pos = index * 10;
        int start = countryBuffer.getInt(pos);
        int end = countryBuffer.getInt(pos + 4);
        char[] countryChars = {
                (char) countryBuffer.get(pos + 8),
                (char) countryBuffer.get(pos + 9)
        };
        String countryCode = new String(countryChars);

        return new GeoRange<>(start, end, countryCode);
    }

    private GeoRange<Integer> readASNRecord(int index) {
        int pos = index * 12;
        int start = asnBuffer.getInt(pos);
        int end = asnBuffer.getInt(pos + 4);
        int asn = asnBuffer.getInt(pos + 8);

        return new GeoRange<>(start, end, asn);
    }

    private List<GeoRange<String>> readAllCountryRanges() {
        List<GeoRange<String>> ranges = new ArrayList<>(countryRecordCount);
        for (int i = 0; i < countryRecordCount; i++) {
            ranges.add(this.readCountryRecord(i));
        }
        ranges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
        return ranges;
    }

    private List<GeoRange<Integer>> readAllASNRanges() {
        List<GeoRange<Integer>> ranges = new ArrayList<>(asnRecordCount);
        for (int i = 0; i < asnRecordCount; i++) {
            ranges.add(this.readASNRecord(i));
        }
        ranges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
        return ranges;
    }

    public @NotNull String getCountryCode(int addressData) {
        return this.getFromCacheOrSearch(addressData, this.countryRangeCache, BinaryGeoIPDatabase.UNKNOWN_COUNTRY);
    }

    public @NotNull Integer getAsnCode(int addressData) {
        return this.getFromCacheOrSearch(addressData, this.asnRangeCache, BinaryGeoIPDatabase.UNKNOWN_ASN);
    }

    private <V> V getFromCacheOrSearch(int ip, List<GeoRange<V>> ranges, V defaultValue) {
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
                return midVal.getValue();
            }
        }

        return defaultValue;
    }
}