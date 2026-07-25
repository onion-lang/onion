package onion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regular expression utilities for Onion programs.
 * All methods are static and can be used without import.
 */
public final class Regex {
    private Regex() {} // Prevent instantiation

    /** Compiled-pattern cache for matchGroups (patterns come from literals, so this stays small). */
    private static final java.util.concurrent.ConcurrentHashMap<String, Pattern> MATCH_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Anchored pattern match used by the {@code case re"..."} select pattern:
     * if the WHOLE input matches, returns the capture groups (index 0 is
     * group 1); otherwise returns null. A group that did not participate in
     * the match yields "".
     */
    public static List<String> matchGroups(String input, String pattern) {
        if (input == null || pattern == null) return null;
        Matcher m = MATCH_CACHE.computeIfAbsent(pattern, Pattern::compile).matcher(input);
        if (!m.matches()) return null;
        List<String> result = new ArrayList<String>();
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            result.add(g != null ? g : "");
        }
        return result;
    }

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
    public static List<String> findAll(String input, String pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            matches.add(matcher.group());
        }
        return matches;
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
    public static List<String> groups(String input, String pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        if (matcher.find()) {
            List<String> result = new ArrayList<String>();
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                result.add(group != null ? group : "");
            }
            return result;
        }
        return new ArrayList<String>();
    }

    /**
     * Returns all capturing groups from all matches.
     * Each element is an array where index 0 is the entire match.
     */
    public static List<List<String>> groupsAll(String input, String pattern) {
        List<List<String>> allGroups = new ArrayList<List<String>>();
        if (input == null || pattern == null) return allGroups;
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            List<String> groups = new ArrayList<String>();
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                groups.add(group != null ? group : "");
            }
            allGroups.add(groups);
        }
        return allGroups;
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
    public static List<String> split(String input, String pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        return new ArrayList<String>(Arrays.asList(input.split(pattern)));
    }

    /**
     * Splits the input by the pattern with a limit on the number of parts.
     */
    public static List<String> split(String input, String pattern, int limit) {
        if (input == null || pattern == null) return new ArrayList<String>();
        return new ArrayList<String>(Arrays.asList(input.split(pattern, limit)));
    }

    // ========== Pattern overloads (interop with re"..." literals) ==========
    // A re"..." literal is a java.util.regex.Pattern; these overloads let the
    // shape-first regex literals be used with the Regex helpers directly.

    public static boolean matches(String input, Pattern pattern) {
        if (input == null || pattern == null) return false;
        return pattern.matcher(input).matches();
    }

    public static boolean find(String input, Pattern pattern) {
        if (input == null || pattern == null) return false;
        return pattern.matcher(input).find();
    }

    public static List<String> findAll(String input, Pattern pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) matches.add(matcher.group());
        return matches;
    }

    public static String findFirst(String input, Pattern pattern) {
        if (input == null || pattern == null) return "";
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group() : "";
    }

    public static List<String> groups(String input, Pattern pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            List<String> result = new ArrayList<String>();
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                result.add(group != null ? group : "");
            }
            return result;
        }
        return new ArrayList<String>();
    }

    public static List<List<String>> groupsAll(String input, Pattern pattern) {
        List<List<String>> allGroups = new ArrayList<List<String>>();
        if (input == null || pattern == null) return allGroups;
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            List<String> groups = new ArrayList<String>();
            for (int i = 0; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                groups.add(group != null ? group : "");
            }
            allGroups.add(groups);
        }
        return allGroups;
    }

    public static String replace(String input, Pattern pattern, String replacement) {
        if (input == null) return "";
        if (pattern == null || replacement == null) return input;
        return pattern.matcher(input).replaceAll(replacement);
    }

    public static String replaceFirst(String input, Pattern pattern, String replacement) {
        if (input == null) return "";
        if (pattern == null || replacement == null) return input;
        return pattern.matcher(input).replaceFirst(replacement);
    }

    public static List<String> split(String input, Pattern pattern) {
        if (input == null || pattern == null) return new ArrayList<String>();
        return new ArrayList<String>(Arrays.asList(pattern.split(input)));
    }

    public static List<String> split(String input, Pattern pattern, int limit) {
        if (input == null || pattern == null) return new ArrayList<String>();
        return new ArrayList<String>(Arrays.asList(pattern.split(input, limit)));
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
