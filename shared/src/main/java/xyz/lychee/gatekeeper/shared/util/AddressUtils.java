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

    public static int ipv4ToInt(InetAddress address) {
        return ipv4ToInt(address.getAddress());
    }

    public static int ipv4ToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                (bytes[3] & 0xFF);
    }

    public static InetAddress intToIpv4(int address) throws UnknownHostException {
        byte[] bytes = new byte[]{
                (byte) ((address >> 24) & 0xFF),
                (byte) ((address >> 16) & 0xFF),
                (byte) ((address >> 8) & 0xFF),
                (byte) (address & 0xFF)
        };
        return InetAddress.getByAddress(bytes);
    }

    public static int ipv4ToInt(String address) {
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

    public static boolean isIpv4(String input) {
        if (input == null || input.isEmpty()) return false;

        int len = input.length();
        if (len < 7 || len > 15) return false;

        int value = 0;
        int dots = 0;
        int lastDotIdx = -1;

        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '.') {
                if (++dots > 3) return false;
                if (i == 0 || input.charAt(i - 1) == '.') return false;
                if (value > 255) return false;
                value = 0;
                lastDotIdx = i;
            } else if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
            } else {
                return false;
            }
        }

        return value < 256 && lastDotIdx != len - 1 && dots == 3;
    }

    public static boolean isIpv4Equal(InetAddress address, int addressData) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && AddressUtils.ipv4ToInt(bytes) == addressData;
    }
}
