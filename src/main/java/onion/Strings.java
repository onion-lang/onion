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
}
