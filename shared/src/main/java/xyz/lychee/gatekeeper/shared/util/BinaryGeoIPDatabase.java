package xyz.lychee.gatekeeper.shared.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

@Getter
public class BinaryGeoIPDatabase {
    public static final int UNKNOWN_ASN = -1;
    public static final String UNKNOWN_COUNTRY = "--";
    public static final int MAGIC_NUMBER = 0x47454F49;
    public static final int VERSION = 1;

    private final String dbFile;
    private ByteBuffer countryBuffer;
    private ByteBuffer asnBuffer;
    private int countryRecordCount;
    private int asnRecordCount;

    public BinaryGeoIPDatabase(String dbFile) {
        this.dbFile = dbFile;
    }

    public void load() throws IOException {
        Path path = Paths.get(dbFile);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Database file not found: " + dbFile);
        }

        byte[] data;
        try (InputStream fileIn = Files.newInputStream(path);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            data = out.toByteArray();
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);

        int magic = buffer.getInt();
        if (magic != MAGIC_NUMBER) {
            throw new IOException("Invalid database format (wrong magic number)");
        }

        int version = buffer.getInt();
        if (version != VERSION) {
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

    public void close() {
        countryBuffer = null;
        asnBuffer = null;
    }

    @Getter
    @AllArgsConstructor
    public static class GeoRange<T> {
        private final int start;
        private final int end;
        private final T value;

        public boolean contains(int ipNum) {
            return Integer.compareUnsigned(ipNum, this.start) >= 0 &&
                    Integer.compareUnsigned(ipNum, this.end) <= 0;
        }
    }
}