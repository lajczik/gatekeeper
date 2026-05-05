package xyz.lychee.gatekeeper.shared.util;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.objects.GeoRange;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

@Getter
public class BinaryGeoIPDatabase {
    public static final int UNKNOWN_ASN = -1;
    public static final String UNKNOWN_COUNTRY = "--";

    private ByteBuffer countryBuffer;
    private ByteBuffer asnBuffer;
    private int countryRecordCount;
    private int asnRecordCount;

    public void load(File databaseFile) throws IOException {
        if (!databaseFile.exists()) {
            throw new FileNotFoundException("Database file not found: " + databaseFile.getAbsolutePath());
        }

        byte[] data;
        try (FileInputStream fis = new FileInputStream(databaseFile)) {
            data = fis.readAllBytes();
        }

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
    }

    private GeoRange<String> getCountryRecord(int index) {
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

    private GeoRange<Integer> getASNRecord(int index) {
        int pos = index * 12;
        int start = asnBuffer.getInt(pos);
        int end = asnBuffer.getInt(pos + 4);
        int asn = asnBuffer.getInt(pos + 8);

        return new GeoRange<>(start, end, asn);
    }

    public Set<GeoRange<String>> getAllCountryRanges() {
        Set<GeoRange<String>> ranges = new HashSet<>(countryRecordCount);
        for (int i = 0; i < countryRecordCount; i++) {
            ranges.add(getCountryRecord(i));
        }
        return ranges;
    }

    public Set<GeoRange<Integer>> getAllASNRanges() {
        Set<GeoRange<Integer>> ranges = new HashSet<>(asnRecordCount);
        for (int i = 0; i < asnRecordCount; i++) {
            ranges.add(getASNRecord(i));
        }
        return ranges;
    }
}