package onion;

import java.util.Locale;

/**
 * Human-readable formatting of numbers, sizes and durations for Onion programs.
 * All output is locale-independent (comma thousands separators, dot decimal
 * point) so results are stable across environments. Call as
 * {@code Format::bytes(1536)}.
 */
public final class Format {
    private Format() {
    }

    /** Formats an integer with comma thousands separators: 1234567 -> "1,234,567". */
    public static String integer(long value) {
        return group(Long.toString(Math.abs(value)), value < 0);
    }

    /** Formats with comma grouping and a fixed number of decimals: (1234.5678, 2) -> "1,234.57". */
    public static String number(double value, int decimals) {
        String fixed = fixed(value, decimals);
        boolean negative = fixed.startsWith("-");
        String body = negative ? fixed.substring(1) : fixed;
        int dot = body.indexOf('.');
        String intPart = dot < 0 ? body : body.substring(0, dot);
        String fracPart = dot < 0 ? "" : body.substring(dot);
        return group(intPart, negative) + fracPart;
    }

    /** Rounds to a fixed number of decimals (no grouping): (3.14159, 2) -> "3.14". */
    public static String fixed(double value, int decimals) {
        int d = Math.max(0, decimals);
        return String.format(Locale.ROOT, "%." + d + "f", value);
    }

    /** Formats a 0..1 ratio as a percentage: (0.756, 1) -> "75.6%". */
    public static String percent(double ratio, int decimals) {
        return fixed(ratio * 100.0, decimals) + "%";
    }

    /** Human-readable byte size (1024-based): 1536 -> "1.5 KB", 500 -> "500 B". */
    public static String bytes(long numBytes) {
        boolean negative = numBytes < 0;
        double v = Math.abs((double) numBytes);
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int unit = 0;
        while (v >= 1024.0 && unit < units.length - 1) {
            v /= 1024.0;
            unit++;
        }
        String text = unit == 0
            ? Long.toString((long) v) + " B"
            : fixed(v, 1) + " " + units[unit];
        return negative ? "-" + text : text;
    }

    /** Human-readable duration from seconds: 3661 -> "1h 1m 1s", 0 -> "0s". */
    public static String duration(long totalSeconds) {
        boolean negative = totalSeconds < 0;
        long s = Math.abs(totalSeconds);
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long minutes = (s % 3600) / 60;
        long seconds = s % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s ");
        String text = sb.toString().trim();
        return negative ? "-" + text : text;
    }

    /** English ordinal: 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 11 -> "11th", 21 -> "21st". */
    public static String ordinal(long n) {
        long abs = Math.abs(n);
        long lastTwo = abs % 100;
        String suffix;
        if (lastTwo >= 11 && lastTwo <= 13) {
            suffix = "th";
        } else {
            switch ((int) (abs % 10)) {
                case 1: suffix = "st"; break;
                case 2: suffix = "nd"; break;
                case 3: suffix = "rd"; break;
                default: suffix = "th";
            }
        }
        return n + suffix;
    }

    private static String group(String digits, boolean negative) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) sb.append(',');
        }
        if (negative) sb.append('-');
        return sb.reverse().toString();
    }
}
