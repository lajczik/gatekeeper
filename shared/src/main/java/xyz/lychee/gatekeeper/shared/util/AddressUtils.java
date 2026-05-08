package xyz.lychee.gatekeeper.shared.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class AddressUtils {
    private AddressUtils() throws IllegalAccessException {
        throw new IllegalAccessException();
    }

    public static String fixHostname(String hostname) {
        int zeroIdx = hostname.indexOf(0);
        String cleaned = zeroIdx > -1 ? hostname.substring(0, zeroIdx) : hostname;
        return !cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) == '.' ? cleaned.substring(0, cleaned.length() - 1) : cleaned;
    }

    public static int addressToInteger(InetAddress address) {
        byte[] bytes = address.getAddress();
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    public static InetAddress integerToAddress(int address) throws UnknownHostException {
        byte[] bytes = new byte[]{
                (byte) ((address >> 24) & 0xFF),
                (byte) ((address >> 16) & 0xFF),
                (byte) ((address >> 8) & 0xFF),
                (byte) (address & 0xFF)
        };
        return InetAddress.getByAddress(bytes);
    }

    public static int addressToInteger(String address) {
        int result = 0;
        int part = 0;
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);
            if (c == '.') {
                result = (result << 8) | part;
                part = 0;
            } else if (c == ':') {
                break;
            } else {
                part = part * 10 + (c - '0');
            }
        }
        return (result << 8) | part;
    }

    public static boolean isIpAddress(String input) {
        return input.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || input.contains(":");
    }

    public static byte[] parseIp(String ip) throws IllegalArgumentException {
        if (ip.contains(":")) {
            return parseIp(ip.split(":")[0]);
        } else {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4");
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int n = Integer.parseInt(parts[i]);
                if (n < 0 || n > 255) throw new IllegalArgumentException("Invalid IPv4");
                bytes[i] = (byte) n;
            }
            return bytes;
        }
    }
}
