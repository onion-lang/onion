package onion;

/**
 * Test assertion utilities for Onion programs.
 *
 * Usage:
 *   Assert::equals("hello", value);
 *   Assert::notNull(obj);
 *   Assert::isTrue(condition);
 *   Assert::fail("should not reach here");
 */
public class Assert {

    // ========== Equality ==========

    /**
     * Asserts that expected equals actual.
     * @throws AssertionError if values are not equal
     */
    public static void equals(Object expected, Object actual) {
        equals(expected, actual, "Expected <" + expected + "> but was <" + actual + ">");
    }

    /**
     * Asserts that expected equals actual with custom message.
     * @throws AssertionError if values are not equal
     */
    public static void equals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message);
        }
    }

    /**
     * Asserts that two values are not equal.
     * @throws AssertionError if values are equal
     */
    public static void notEquals(Object unexpected, Object actual) {
        notEquals(unexpected, actual, "Expected not <" + unexpected + "> but was equal");
    }

    /**
     * Asserts that two values are not equal with custom message.
     * @throws AssertionError if values are equal
     */
    public static void notEquals(Object unexpected, Object actual, String message) {
        if (unexpected == null ? actual == null : unexpected.equals(actual)) {
            throw new AssertionError(message);
        }
    }

    // ========== Null checks ==========

    /**
     * Asserts that value is not null.
     * @throws AssertionError if value is null
     */
    public static void notNull(Object value) {
        notNull(value, "Expected non-null value but was null");
    }

    /**
     * Asserts that value is not null with custom message.
     * @throws AssertionError if value is null
     */
    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /**
     * Asserts that value is null.
     * @throws AssertionError if value is not null
     */
    public static void isNull(Object value) {
        isNull(value, "Expected null but was <" + value + ">");
    }

    /**
     * Asserts that value is null with custom message.
     * @throws AssertionError if value is not null
     */
    public static void isNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message);
        }
    }

    // ========== Boolean checks ==========

    /**
     * Asserts that condition is true.
     * @throws AssertionError if condition is false
     */
    public static void isTrue(boolean condition) {
        isTrue(condition, "Expected true but was false");
    }

    /**
     * Asserts that condition is true with custom message.
     * @throws AssertionError if condition is false
     */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Asserts that condition is false.
     * @throws AssertionError if condition is true
     */
    public static void isFalse(boolean condition) {
        isFalse(condition, "Expected false but was true");
    }

    /**
     * Asserts that condition is false with custom message.
     * @throws AssertionError if condition is true
     */
    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    // ========== Fail ==========

    /**
     * Fails the test unconditionally.
     * @throws AssertionError always
     */
    public static void fail() {
        fail("Test failed");
    }

    /**
     * Fails the test unconditionally with custom message.
     * @throws AssertionError always
     */
    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
