package onion;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.io.IOException;

/**
 * Configuration management module for loading and accessing JSON config files.
 * Supports dot notation for nested values and environment variable access.
 *
 * Usage:
 *   val config = Config::loadJson("config.json");
 *   val host = Config::getString(config, "database.host", "localhost");
 *   val port = Config::getInt(config, "database.port", 5432);
 */
public class Config {

    /**
     * Loads JSON configuration from a file.
     * @param path Path to the JSON file
     * @return Parsed JSON object (LinkedHashMap for objects, ArrayList for arrays)
     * @throws IOException if file cannot be read
     * @throws Json.JsonParseException if JSON parsing fails
     */
    public static Object loadJson(String path) throws IOException, Json.JsonParseException {
        String content = Files.readText(path);
        return Json.parse(content);
    }

    /**
     * Parses JSON string directly into a config object.
     * @param json JSON string to parse
     * @return Parsed JSON object
     * @throws Json.JsonParseException if JSON parsing fails
     */
    public static Object parseJson(String json) throws Json.JsonParseException {
        return Json.parse(json);
    }

    /**
     * Gets a value at the specified dot-notation path.
     * Supports array indexing with numeric keys (e.g., "items.0").
     * @param config The config object
     * @param path Dot-separated path (e.g., "database.host")
     * @return Value at path, or null if not found
     */
    public static Object get(Object config, String path) {
        String[] keys = path.split("\\.");
        Object current = config;
        for (String key : keys) {
            if (current == null) return null;
            if (current instanceof LinkedHashMap) {
                current = ((LinkedHashMap<?, ?>) current).get(key);
            } else if (current instanceof ArrayList && key.matches("\\d+")) {
                int index = Integer.parseInt(key);
                ArrayList<?> list = (ArrayList<?>) current;
                if (index >= 0 && index < list.size()) {
                    current = list.get(index);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Gets a string value at path, or returns default if not found.
     */
    public static String getString(Object config, String path, String defaultValue) {
        Object value = get(config, path);
        if (value == null) return defaultValue;
        return value.toString();
    }

    /**
     * Gets an integer value at path, or returns default if not found or not a number.
     */
    public static int getInt(Object config, String path, int defaultValue) {
        Object value = get(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a long value at path, or returns default if not found or not a number.
     */
    public static long getLong(Object config, String path, long defaultValue) {
        Object value = get(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a double value at path, or returns default if not found or not a number.
     */
    public static double getDouble(Object config, String path, double defaultValue) {
        Object value = get(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean value at path, or returns default if not found.
     */
    public static boolean getBoolean(Object config, String path, boolean defaultValue) {
        Object value = get(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Gets an environment variable, or returns default if not set.
     */
    public static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a config value with environment variable override.
     * Environment variable takes precedence if set.
     */
    public static String getWithEnvOverride(Object config, String path,
                                            String envName, String defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null) return envValue;
        return getString(config, path, defaultValue);
    }

    /**
     * Checks if a path exists in the config.
     */
    public static boolean hasPath(Object config, String path) {
        return get(config, path) != null;
    }
}
