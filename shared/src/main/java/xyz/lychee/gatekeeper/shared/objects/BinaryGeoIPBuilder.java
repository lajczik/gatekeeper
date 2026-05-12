package xyz.lychee.gatekeeper.shared.objects;

import xyz.lychee.gatekeeper.shared.manager.TaskManager;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.*;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BinaryGeoIPBuilder {
    public static final int MAGIC_NUMBER = 0x47454F49;
    public static final int VERSION = 1;
    private static final int MAX_GAP = 256;

    public static CompletableFuture<Void> buildDatabase(Logger logger, Path output) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://github.com/iplocate/ip-address-databases/raw/refs/heads/main/ip-to-asn/ip-to-asn.csv.zip"))
                .GET()
                .build();

        return TaskManager.INSTANCE.getHttpClient()
                .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAcceptAsync(response -> {
                    try (ZipInputStream zis = new ZipInputStream(response.body());
                         BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (!entry.isDirectory()) break;
                        }

                        List<GeoRange<String>> countryRanges = new ArrayList<>(600000);
                        List<GeoRange<Integer>> asnRanges = new ArrayList<>(600000);

                        Map<String, String> countryCodeCache = new HashMap<>(256);

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) continue;

                            int firstComma = line.indexOf(',');
                            if (firstComma == -1) continue;

                            int secondComma = line.indexOf(',', firstComma + 1);
                            if (secondComma == -1) continue;

                            int thirdComma = line.indexOf(',', secondComma + 1);
                            if (thirdComma == -1) continue;

                            String network = line.substring(0, firstComma);
                            if (!network.contains(".")) continue;

                            long[] ipRange = parseNetwork(network);
                            if (ipRange == null) continue;

                            int start = (int) ipRange[0];
                            int end = (int) ipRange[1];

                            String asnStr = line.substring(firstComma + 1, secondComma);
                            if (!asnStr.isEmpty() && RandomUtils.isInteger(asnStr)) {
                                int asn = Integer.parseInt(asnStr);
                                asnRanges.add(new GeoRange<>(start, end, asn));
                            }

                            int countryLen = thirdComma - (secondComma + 1);
                            if (countryLen >= 2) {
                                String rawCountryCode = line.substring(secondComma + 1, secondComma + 3);
                                String cachedCode = countryCodeCache.computeIfAbsent(rawCountryCode, k -> k);

                                countryRanges.add(new GeoRange<>(start, end, cachedCode));
                            }
                        }

                        // logger.info("extracted ranges: country="+countryRanges.size()+" asn="+asnRanges.size());
                        countryRanges = flattenRanges(countryRanges);
                        asnRanges = flattenRanges(asnRanges);

                        // logger.info("flatten ranges: country="+countryRanges.size()+" asn="+asnRanges.size());
                        countryRanges = compressRanges(countryRanges);
                        asnRanges = compressRanges(asnRanges);

                        // logger.info("compressed ranges: country="+countryRanges.size()+" asn="+asnRanges.size());
                        countryRanges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
                        asnRanges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));

                        writeBinaryDatabase(output, countryRanges, asnRanges);
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "Error while building geo database file", ex);
                    }
                }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    private static long[] parseNetwork(String network) {
        int slashIdx = network.indexOf('/');
        if (slashIdx == -1) return null;

        try {
            int ipNum = AddressUtils.ipv4ToInt(network.substring(0, slashIdx));
            int prefix = Integer.parseInt(network.substring(slashIdx + 1));

            long mask = 0xFFFFFFFFL << (32 - prefix);
            long start = Integer.toUnsignedLong(ipNum) & mask;
            long end = start | (~mask & 0xFFFFFFFFL);

            return new long[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> List<GeoRange<T>> flattenRanges(List<GeoRange<T>> inputRanges) {
        if (inputRanges.isEmpty()) return inputRanges;

        List<Event<T>> events = new ArrayList<>(inputRanges.size() * 2);

        for (GeoRange<T> range : inputRanges) {
            long start = Integer.toUnsignedLong(range.getStart());
            long end = Integer.toUnsignedLong(range.getEnd());

            events.add(new Event<>(start, true, range));
            events.add(new Event<>(end + 1L, false, range));
        }

        events.sort(Comparator.comparingLong(e -> e.ip));

        List<GeoRange<T>> flattened = new ArrayList<>();
        List<GeoRange<T>> activeRanges = new ArrayList<>();

        long currentStartIp = -1;
        GeoRange<T> currentBestRange = null;

        int i = 0;
        while (i < events.size()) {
            long currentIp = events.get(i).ip;

            while (i < events.size() && events.get(i).ip == currentIp) {
                Event<T> e = events.get(i);
                if (e.isStart) {
                    activeRanges.add(e.range);
                } else {
                    activeRanges.remove(e.range);
                }
                i++;
            }

            GeoRange<T> nextBestRange = getNarrowest(activeRanges);

            if (currentBestRange != nextBestRange) {
                if (currentBestRange != null && currentStartIp < currentIp) {
                    flattened.add(new GeoRange<>(
                            (int) currentStartIp,
                            (int) (currentIp - 1L),
                            currentBestRange.getValue()
                    ));
                }
                currentStartIp = currentIp;
                currentBestRange = nextBestRange;
            }
        }

        return flattened;
    }

    private static <T> GeoRange<T> getNarrowest(List<GeoRange<T>> active) {
        GeoRange<T> best = null;
        long minLen = Long.MAX_VALUE;

        for (GeoRange<T> r : active) {
            long len = Integer.toUnsignedLong(r.getEnd()) - Integer.toUnsignedLong(r.getStart());
            if (len < minLen) {
                minLen = len;
                best = r;
            }
        }
        return best;
    }

    private static <T> List<GeoRange<T>> compressRanges(List<GeoRange<T>> ranges) {
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

    private static <T> boolean canMerge(GeoRange<T> a, GeoRange<T> b) {
        long gap = Integer.toUnsignedLong(b.getStart()) - Integer.toUnsignedLong(a.getEnd()) - 1;

        return gap <= MAX_GAP && a.getValue().equals(b.getValue());
    }

    private static <T> GeoRange<T> merge(GeoRange<T> a, GeoRange<T> b) {
        int newEnd = Integer.compareUnsigned(a.getEnd(), b.getEnd()) > 0 ? a.getEnd() : b.getEnd();

        return new GeoRange<>(a.getStart(), newEnd, a.getValue());
    }

    private static void writeBinaryDatabase(Path output, List<GeoRange<String>> countryRanges, List<GeoRange<Integer>> asnRanges) throws IOException {
        try (FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
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

    private static class Event<T> {
        long ip;
        boolean isStart;
        GeoRange<T> range;

        Event(long ip, boolean isStart, GeoRange<T> range) {
            this.ip = ip;
            this.isStart = isStart;
            this.range = range;
        }
    }
}