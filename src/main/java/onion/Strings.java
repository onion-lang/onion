package onion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * String utility functions for Onion programs.
 * All methods are static and can be imported via: import static onion.Strings::*
 */
public final class Strings {
    private Strings() {} // Prevent instantiation

    // Splitting and joining
    public static String[] split(String str, String delimiter) {
        if (str == null) return new String[0];
        return str.split(Pattern.quote(delimiter));
    }

    public static String[] splitRegex(String str, String regex) {
        if (str == null) return new String[0];
        return str.split(regex);
    }

    public static String join(String[] parts, String delimiter) {
        if (parts == null) return "";
        return String.join(delimiter, parts);
    }

    public static String join(List<?> parts, String delimiter) {
        if (parts == null) return "";
        return String.join(delimiter, parts.stream()
            .map(Object::toString)
            .toArray(String[]::new));
    }

    // Transformation
    public static String trim(String str) {
        return str == null ? "" : str.trim();
    }

    public static String upper(String str) {
        return str == null ? "" : str.toUpperCase();
    }

    public static String lower(String str) {
        return str == null ? "" : str.toLowerCase();
    }

    public static String replace(String str, String target, String replacement) {
        if (str == null) return "";
        return str.replace(target, replacement);
    }

    public static String replaceRegex(String str, String regex, String replacement) {
        if (str == null) return "";
        return str.replaceAll(regex, replacement);
    }

    // Inspection
    public static boolean startsWith(String str, String prefix) {
        return str != null && str.startsWith(prefix);
    }

    public static boolean endsWith(String str, String suffix) {
        return str != null && str.endsWith(suffix);
    }

    public static boolean contains(String str, String substring) {
        return str != null && str.contains(substring);
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    // Extraction
    public static String substring(String str, int start) {
        if (str == null || start >= str.length()) return "";
        if (start < 0) start = 0;
        return str.substring(start);
    }

    public static String substring(String str, int start, int end) {
        if (str == null) return "";
        if (start < 0) start = 0;
        if (start >= str.length()) return "";
        if (end > str.length()) end = str.length();
        if (end <= start) return "";
        return str.substring(start, end);
    }

    public static int indexOf(String str, String substring) {
        return str == null ? -1 : str.indexOf(substring);
    }

    public static int lastIndexOf(String str, String substring) {
        return str == null ? -1 : str.lastIndexOf(substring);
    }

    // Padding and formatting
    public static String padLeft(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder();
        for (int i = str.length(); i < length; i++) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }

    public static String padRight(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    public static String repeat(String str, int count) {
        if (str == null || count <= 0) return "";
        return str.repeat(count);
    }

    // Conversion
    public static String[] lines(String str) {
        if (str == null) return new String[0];
        return str.split("\\r?\\n");
    }

    public static String reverse(String str) {
        if (str == null) return "";
        return new StringBuilder(str).reverse().toString();
    }

    // Case helpers
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str == null ? "" : str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) return str == null ? "" : str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /** Capitalizes the first letter of each whitespace-separated word. */
    public static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str == null ? "" : str;
        StringBuilder sb = new StringBuilder(str.length());
        boolean atStart = true;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isWhitespace(c)) {
                atStart = true;
                sb.append(c);
            } else if (atStart) {
                sb.append(Character.toUpperCase(c));
                atStart = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    // Inspection extras
    public static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    public static boolean containsIgnoreCase(String str, String substring) {
        if (str == null || substring == null) return false;
        return str.toLowerCase().contains(substring.toLowerCase());
    }

    /** Counts non-overlapping occurrences of {@code substring} in {@code str}. */
    public static int count(String str, String substring) {
        if (str == null || substring == null || substring.isEmpty()) return 0;
        int n = 0;
        int from = 0;
        while (true) {
            int idx = str.indexOf(substring, from);
            if (idx < 0) break;
            n++;
            from = idx + substring.length();
        }
        return n;
    }

    // Trimming affixes
    public static String removePrefix(String str, String prefix) {
        if (str == null) return "";
        if (prefix != null && str.startsWith(prefix)) return str.substring(prefix.length());
        return str;
    }

    public static String removeSuffix(String str, String suffix) {
        if (str == null) return "";
        if (suffix != null && str.endsWith(suffix)) return str.substring(0, str.length() - suffix.length());
        return str;
    }

    // Shaping
    /** Truncates {@code str} to {@code maxLength} total characters, appending {@code suffix}. */
    public static String truncate(String str, int maxLength, String suffix) {
        if (str == null) return "";
        if (maxLength < 0) maxLength = 0;
        if (str.length() <= maxLength) return str;
        String tail = suffix == null ? "" : suffix;
        int keep = maxLength - tail.length();
        if (keep <= 0) return tail.length() <= maxLength ? tail : tail.substring(0, maxLength);
        return str.substring(0, keep) + tail;
    }

    /** Pads {@code str} on both sides to reach {@code length}. */
    public static String center(String str, int length, char padChar) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        int total = length - str.length();
        int left = total / 2;
        int right = total - left;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < left; i++) sb.append(padChar);
        sb.append(str);
        for (int i = 0; i < right; i++) sb.append(padChar);
        return sb.toString();
    }

    /** Returns {@code fallback} if {@code str} is null or blank, otherwise {@code str}. */
    public static String ifBlank(String str, String fallback) {
        return (str == null || str.trim().isEmpty()) ? fallback : str;
    }

    // Decomposition
    /** Splits on runs of whitespace, dropping empty tokens. */
    public static String[] words(String str) {
        if (str == null) return new String[0];
        String trimmed = str.trim();
        if (trimmed.isEmpty()) return new String[0];
        return trimmed.split("\\s+");
    }

    /** Returns each character as a single-character string. */
    public static List<String> chars(String str) {
        List<String> result = new ArrayList<>();
        if (str == null) return result;
        for (int i = 0; i < str.length(); i++) {
            result.add(String.valueOf(str.charAt(i)));
        }
        return result;
    }

    // Null-safe parsing
    /** Parses an int, or returns null if {@code str} is not a valid integer. */
    public static Integer toIntOrNull(String str) {
        if (str == null) return null;
        try {
            return Integer.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a long, or returns null if {@code str} is not a valid long. */
    public static Long toLongOrNull(String str) {
        if (str == null) return null;
        try {
            return Long.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a double, or returns null if {@code str} is not a valid number. */
    public static Double toDoubleOrNull(String str) {
        if (str == null) return null;
        try {
            return Double.valueOf(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses an int, or returns {@code fallback} if {@code str} is not a valid integer. */
    public static int toIntOr(String str, int fallback) {
        Integer v = toIntOrNull(str);
        return v == null ? fallback : v;
    }
}
