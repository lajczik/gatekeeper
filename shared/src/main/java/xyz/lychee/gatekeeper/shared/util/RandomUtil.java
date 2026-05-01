package xyz.lychee.gatekeeper.shared.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class RandomUtil {
    public static final FastRandom RANDOM = new FastRandom();
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    public static final Pattern PATTERN = Pattern.compile("-?[0-9]+");

    public static boolean isInteger(String string) {
        return string != null && !string.isEmpty() && PATTERN.matcher(string).matches();
    }

    public static boolean chance(double chance) {
        return chance >= 100D || chance >= RANDOM.nextDouble() * 100D;
    }

    public static double round(double value, int decimals) {
        double p = Math.pow(10, decimals);
        return Math.round(value * p) / p;
    }

    public static int randomInt(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return min == max ? max : RANDOM.nextInt(min, max + 1);
    }

    public static double randomDouble(double a, double b) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);
        return min == max ? max : RANDOM.nextDouble(min, max);
    }

    public static float randomFloat(float a, float b) {
        float min = Math.min(a, b);
        float max = Math.max(a, b);
        return min == max ? max : RANDOM.nextFloat(min, max);
    }

    public static String getDurationBreakdown(long time, TimeUnit unit) {
        if (time <= 0) {
            return unit == TimeUnit.MILLISECONDS ? "0ms" : "0s";
        }

        long totalMillis = unit.toMillis(time);
        long days = totalMillis / 86400000L;
        long hours = (totalMillis % 86400000L) / 3600000L;
        long minutes = (totalMillis % 3600000L) / 60000L;

        StringBuilder sb = new StringBuilder(32);

        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");

        if (days > 0) {
            if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }

        long seconds = (totalMillis % 60000L) / 1000L;
        long millis = totalMillis % 1000L;

        if (unit == TimeUnit.MILLISECONDS && millis > 0) {
            sb.append(seconds).append('.');
            if (millis < 100) sb.append('0');
            if (millis < 10) sb.append('0');
            sb.append(millis).append('s');
        } else {
            if (seconds > 0) {
                sb.append(seconds).append('s');
            } else if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            } else {
                sb.append("0s");
            }
        }

        return sb.toString();
    }

    public static String getDate(long time) {
        return DATE_FORMAT.format(new Date(time));
    }
}