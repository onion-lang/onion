package onion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regular expression utilities for Onion programs.
 * All methods are static and can be used without import.
 */
public final class Regex {
    private Regex() {} // Prevent instantiation

    // ========== Matching ==========

    /**
     * Returns true if the entire input matches the pattern.
     */
    public static boolean matches(String input, String pattern) {
        if (input == null || pattern == null) return false;
        return input.matches(pattern);
    }

    /**
     * Returns true if the pattern is found anywhere in the input.
     */
    public static boolean find(String input, String pattern) {
        if (input == null || pattern == null) return false;
        return Pattern.compile(pattern).matcher(input).find();
    }

    // ========== Extraction ==========

    /**
     * Returns all matches of the pattern in the input.
     */
    public static String[] findAll(String input, String pattern) {
        if (input == null || pattern == null) return new String[0];
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches.toArray(new String[0]);
    }

    /**
     * Returns the first match of the pattern, or empty string if not found.
     */
    public static String findFirst(String input, String pattern) {
        if (input == null || pattern == null) return "";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    /**
     * Returns all capturing groups from the first match.
     * Index 0 is the entire match, index 1+ are the groups.
     */
    public static String[] groups(String input, String pattern) {
        if (input == null || pattern == null) return new String[0];
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        if (matcher.find()) {
            String[] result = new String[matcher.groupCount() + 1];
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                result[i] = group != null ? group : "";
            }
            return result;
        }
        return new String[0];
    }

    /**
     * Returns all capturing groups from all matches.
     * Each element is an array where index 0 is the entire match.
     */
    public static String[][] groupsAll(String input, String pattern) {
        if (input == null || pattern == null) return new String[0][];
        List<String[]> allGroups = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String[] groups = new String[matcher.groupCount() + 1];
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                groups[i] = group != null ? group : "";
            }
            allGroups.add(groups);
        }
        return allGroups.toArray(new String[0][]);
    }

    // ========== Replacement ==========

    /**
     * Replaces all occurrences of the pattern with the replacement.
     */
    public static String replace(String input, String pattern, String replacement) {
        if (input == null) return "";
        if (pattern == null || replacement == null) return input;
        return input.replaceAll(pattern, replacement);
    }

    /**
     * Replaces the first occurrence of the pattern with the replacement.
     */
    public static String replaceFirst(String input, String pattern, String replacement) {
        if (input == null) return "";
        if (pattern == null || replacement == null) return input;
        return input.replaceFirst(pattern, replacement);
    }

    // ========== Splitting ==========

    /**
     * Splits the input by the pattern.
     */
    public static String[] split(String input, String pattern) {
        if (input == null || pattern == null) return new String[0];
        return input.split(pattern);
    }

    /**
     * Splits the input by the pattern with a limit on the number of parts.
     */
    public static String[] split(String input, String pattern, int limit) {
        if (input == null || pattern == null) return new String[0];
        return input.split(pattern, limit);
    }

    // ========== Utility ==========

    /**
     * Returns a literal pattern string for the given string.
     * Special regex characters are escaped.
     */
    public static String quote(String literal) {
        if (literal == null) return "";
        return Pattern.quote(literal);
    }

    /**
     * Returns true if the pattern is a valid regular expression.
     */
    public static boolean isValid(String pattern) {
        if (pattern == null) return false;
        try {
            Pattern.compile(pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
