package xyz.lychee.gatekeeper.shared.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import xyz.lychee.gatekeeper.shared.objects.GeoRange;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BinaryGeoIPBuilder {
    public static final int MAGIC_NUMBER = 0x47454F49;
    public static final int VERSION = 1;
    private static final int MAX_GAP = 256;

    private static int ipToInt(String ipAddress) {
        int result = 0;
        int part = 0;
        int len = ipAddress.length();

        for (int i = 0; i < len; i++) {
            char c = ipAddress.charAt(i);
            if (c == '.') {
                result = (result << 8) | part;
                part = 0;
            } else {
                part = part * 10 + (c - '0');
            }
        }
        return (result << 8) | part;
    }

    public void downloadSource(File sourceCsv) throws IOException {
        URI uri = URI.create("https://github.com/iplocate/ip-address-databases/raw/refs/heads/main/ip-to-asn/ip-to-asn.csv.zip");
        try (InputStream is = uri.toURL().openStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                try (FileOutputStream fos = new FileOutputStream(sourceCsv)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                break;
            }
        }
    }

    public void buildDatabase(File sourceCsv, File output) throws IOException {
        List<GeoRange<String>> countryRanges = new ArrayList<>(600000);
        List<GeoRange<Integer>> asnRanges = new ArrayList<>(600000);

        try (Reader reader = new InputStreamReader(new FileInputStream(sourceCsv), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {

            for (CSVRecord rec : parser) {
                String network = rec.get("network");

                if (!network.contains(".")) continue;

                long[] ipRange = parseNetwork(network);
                if (ipRange == null) continue;

                int start = (int) ipRange[0];
                int end = (int) ipRange[1];

                String asnStr = rec.get("asn");
                if (RandomUtil.isInteger(asnStr)) {
                    asnRanges.add(new GeoRange<>(start, end, Integer.parseInt(asnStr)));
                }

                String countryCode = rec.get("country_code");
                if (countryCode != null && countryCode.length() >= 2) {
                    countryRanges.add(new GeoRange<>(start, end, countryCode.substring(0, 2)));
                }
            }
        }

        countryRanges = compressRanges(countryRanges);
        asnRanges = compressRanges(asnRanges);

        countryRanges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
        asnRanges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));

        writeBinaryDatabase(output, countryRanges, asnRanges);
    }

    private long[] parseNetwork(String network) {
        int slashIdx = network.indexOf('/');
        if (slashIdx == -1) return null;

        try {
            int ipNum = ipToInt(network.substring(0, slashIdx));
            int prefix = Integer.parseInt(network.substring(slashIdx + 1));

            long mask = 0xFFFFFFFFL << (32 - prefix);
            long start = Integer.toUnsignedLong(ipNum) & mask;
            long end = start | (~mask & 0xFFFFFFFFL);

            return new long[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<GeoRange<T>> compressRanges(List<GeoRange<T>> ranges) {
        if (ranges.isEmpty()) return ranges;

        ranges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
        List<GeoRange<T>> compressed = new ArrayList<>(ranges.size());

        GeoRange<T> current = ranges.getFirst();

        for (int i = 1; i < ranges.size(); i++) {
            GeoRange<T> next = ranges.get(i);

            if (canMerge(current, next)) {
                current = merge(current, next);
            } else {
                compressed.add(current);
                current = next;
            }
        }
        compressed.add(current);
        return compressed;
    }

    private <T> boolean canMerge(GeoRange<T> a, GeoRange<T> b) {
        long gap = Integer.toUnsignedLong(b.getStart()) - Integer.toUnsignedLong(a.getEnd()) - 1;

        return gap <= MAX_GAP && a.getValue().equals(b.getValue());
    }

    private <T> GeoRange<T> merge(GeoRange<T> a, GeoRange<T> b) {
        int newEnd = Integer.compareUnsigned(a.getEnd(), b.getEnd()) > 0 ? a.getEnd() : b.getEnd();

        return new GeoRange<>(a.getStart(), newEnd, a.getValue());
    }

    private void writeBinaryDatabase(File output, List<GeoRange<String>> countryRanges, List<GeoRange<Integer>> asnRanges) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(output, "rw");
             FileChannel channel = file.getChannel()) {

            int headerSize = 16;
            int countryDataSize = countryRanges.size() * 10;
            int asnDataSize = asnRanges.size() * 12;

            ByteBuffer buffer = ByteBuffer.allocateDirect(headerSize + countryDataSize + asnDataSize);

            buffer.putInt(MAGIC_NUMBER);
            buffer.putInt(VERSION);
            buffer.putInt(countryRanges.size());
            buffer.putInt(asnRanges.size());

            for (GeoRange<String> range : countryRanges) {
                buffer.putInt(range.getStart());
                buffer.putInt(range.getEnd());
                String cc = range.getValue();
                buffer.put((byte) cc.charAt(0));
                buffer.put((byte) (cc.length() > 1 ? cc.charAt(1) : ' '));
            }

            for (GeoRange<Integer> range : asnRanges) {
                buffer.putInt(range.getStart());
                buffer.putInt(range.getEnd());
                buffer.putInt(range.getValue());
            }

            buffer.flip();
            channel.write(buffer);
        }
    }
}