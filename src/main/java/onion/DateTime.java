package onion;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Date and time utilities for Onion programs.
 * Uses epoch milliseconds as the primary time representation.
 * All methods are static and can be used without import.
 */
public final class DateTime {
    private DateTime() {} // Prevent instantiation

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId LOCAL = ZoneId.systemDefault();
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ========== Current Time ==========

    /**
     * Returns the current time as epoch milliseconds.
     */
    public static long now() {
        return System.currentTimeMillis();
    }

    /**
     * Returns the current time as an ISO-formatted string (local timezone).
     */
    public static String nowString() {
        return LocalDateTime.now().format(ISO_FORMAT);
    }

    /**
     * Returns the current time formatted with the given pattern.
     */
    public static String nowString(String pattern) {
        if (pattern == null) return nowString();
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    // ========== Parsing ==========

    /**
     * Parses an ISO-formatted date-time string to epoch milliseconds.
     */
    public static long parse(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) return 0;
        try {
            LocalDateTime ldt = LocalDateTime.parse(dateTime, ISO_FORMAT);
            return ldt.atZone(LOCAL).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parses a date-time string with the given pattern to epoch milliseconds.
     */
    public static long parse(String dateTime, String pattern) {
        if (dateTime == null || dateTime.isEmpty()) return 0;
        if (pattern == null) return parse(dateTime);
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime ldt = LocalDateTime.parse(dateTime, formatter);
            return ldt.atZone(LOCAL).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    // ========== Formatting ==========

    /**
     * Formats epoch milliseconds as an ISO date-time string.
     */
    public static String format(long epochMillis) {
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), LOCAL);
        return ldt.format(ISO_FORMAT);
    }

    /**
     * Formats epoch milliseconds with the given pattern.
     */
    public static String format(long epochMillis, String pattern) {
        if (pattern == null) return format(epochMillis);
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), LOCAL);
        return ldt.format(DateTimeFormatter.ofPattern(pattern));
    }

    // ========== Components ==========

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), LOCAL);
    }

    /**
     * Returns the year component.
     */
    public static int year(long epochMillis) {
        return toLocalDateTime(epochMillis).getYear();
    }

    /**
     * Returns the month component (1-12).
     */
    public static int month(long epochMillis) {
        return toLocalDateTime(epochMillis).getMonthValue();
    }

    /**
     * Returns the day of month component (1-31).
     */
    public static int day(long epochMillis) {
        return toLocalDateTime(epochMillis).getDayOfMonth();
    }

    /**
     * Returns the hour component (0-23).
     */
    public static int hour(long epochMillis) {
        return toLocalDateTime(epochMillis).getHour();
    }

    /**
     * Returns the minute component (0-59).
     */
    public static int minute(long epochMillis) {
        return toLocalDateTime(epochMillis).getMinute();
    }

    /**
     * Returns the second component (0-59).
     */
    public static int second(long epochMillis) {
        return toLocalDateTime(epochMillis).getSecond();
    }

    /**
     * Returns the day of week (1=Monday, 7=Sunday).
     */
    public static int dayOfWeek(long epochMillis) {
        return toLocalDateTime(epochMillis).getDayOfWeek().getValue();
    }

    /**
     * Returns the day of year (1-366).
     */
    public static int dayOfYear(long epochMillis) {
        return toLocalDateTime(epochMillis).getDayOfYear();
    }

    // ========== Arithmetic ==========

    /**
     * Adds days to the given time.
     */
    public static long addDays(long epochMillis, int days) {
        return epochMillis + (long) days * 24 * 60 * 60 * 1000;
    }

    /**
     * Adds hours to the given time.
     */
    public static long addHours(long epochMillis, int hours) {
        return epochMillis + (long) hours * 60 * 60 * 1000;
    }

    /**
     * Adds minutes to the given time.
     */
    public static long addMinutes(long epochMillis, int minutes) {
        return epochMillis + (long) minutes * 60 * 1000;
    }

    /**
     * Adds seconds to the given time.
     */
    public static long addSeconds(long epochMillis, int seconds) {
        return epochMillis + (long) seconds * 1000;
    }

    /**
     * Adds months to the given time.
     */
    public static long addMonths(long epochMillis, int months) {
        LocalDateTime ldt = toLocalDateTime(epochMillis).plusMonths(months);
        return ldt.atZone(LOCAL).toInstant().toEpochMilli();
    }

    /**
     * Adds years to the given time.
     */
    public static long addYears(long epochMillis, int years) {
        LocalDateTime ldt = toLocalDateTime(epochMillis).plusYears(years);
        return ldt.atZone(LOCAL).toInstant().toEpochMilli();
    }

    // ========== Comparison ==========

    /**
     * Returns the difference in milliseconds (time1 - time2).
     */
    public static long diff(long time1, long time2) {
        return time1 - time2;
    }

    /**
     * Returns the difference in days (time1 - time2).
     */
    public static int diffDays(long time1, long time2) {
        return (int) ((time1 - time2) / (24 * 60 * 60 * 1000));
    }

    /**
     * Returns true if time1 is before time2.
     */
    public static boolean isBefore(long time1, long time2) {
        return time1 < time2;
    }

    /**
     * Returns true if time1 is after time2.
     */
    public static boolean isAfter(long time1, long time2) {
        return time1 > time2;
    }

    // ========== Factory ==========

    /**
     * Creates epoch milliseconds from date components.
     */
    public static long of(int year, int month, int day) {
        return of(year, month, day, 0, 0, 0);
    }

    /**
     * Creates epoch milliseconds from date and time components.
     */
    public static long of(int year, int month, int day, int hour, int minute, int second) {
        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
        return ldt.atZone(LOCAL).toInstant().toEpochMilli();
    }

    // ========== Utility ==========

    /**
     * Returns the start of the day (00:00:00) for the given time.
     */
    public static long startOfDay(long epochMillis) {
        LocalDateTime ldt = toLocalDateTime(epochMillis).toLocalDate().atStartOfDay();
        return ldt.atZone(LOCAL).toInstant().toEpochMilli();
    }

    /**
     * Returns the end of the day (23:59:59.999) for the given time.
     */
    public static long endOfDay(long epochMillis) {
        LocalDateTime ldt = toLocalDateTime(epochMillis).toLocalDate().atTime(23, 59, 59, 999_000_000);
        return ldt.atZone(LOCAL).toInstant().toEpochMilli();
    }
}
