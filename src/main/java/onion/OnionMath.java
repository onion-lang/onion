package onion;

/**
 * Math module providing mathematical functions for Onion programs.
 * All methods are static and can be called using Math::methodName() syntax.
 *
 * Example usage:
 * <pre>
 * val x = Math::sin(3.14159 / 2)    // 1.0
 * val y = Math::sqrt(16.0)          // 4.0
 * val z = Math::pow(2.0, 10.0)      // 1024.0
 * val r = Math::random()            // 0.0 <= r < 1.0
 * </pre>
 */
public final class OnionMath {
    private OnionMath() {
        // Prevent instantiation
    }

    // Constants
    /** The mathematical constant pi (π) */
    public static final double PI = java.lang.Math.PI;

    /** The mathematical constant e (Euler's number) */
    public static final double E = java.lang.Math.E;

    // Trigonometric functions

    /**
     * Returns the trigonometric sine of an angle.
     * @param x an angle, in radians
     * @return the sine of the argument
     */
    public static double sin(double x) {
        return java.lang.Math.sin(x);
    }

    /**
     * Returns the trigonometric cosine of an angle.
     * @param x an angle, in radians
     * @return the cosine of the argument
     */
    public static double cos(double x) {
        return java.lang.Math.cos(x);
    }

    /**
     * Returns the trigonometric tangent of an angle.
     * @param x an angle, in radians
     * @return the tangent of the argument
     */
    public static double tan(double x) {
        return java.lang.Math.tan(x);
    }

    /**
     * Returns the arc sine of a value.
     * @param x the value whose arc sine is to be returned
     * @return the arc sine of the argument in radians
     */
    public static double asin(double x) {
        return java.lang.Math.asin(x);
    }

    /**
     * Returns the arc cosine of a value.
     * @param x the value whose arc cosine is to be returned
     * @return the arc cosine of the argument in radians
     */
    public static double acos(double x) {
        return java.lang.Math.acos(x);
    }

    /**
     * Returns the arc tangent of a value.
     * @param x the value whose arc tangent is to be returned
     * @return the arc tangent of the argument in radians
     */
    public static double atan(double x) {
        return java.lang.Math.atan(x);
    }

    /**
     * Returns the angle theta from the conversion of rectangular coordinates (x, y)
     * to polar coordinates (r, theta).
     * @param y the ordinate coordinate
     * @param x the abscissa coordinate
     * @return the theta component of the point (r, theta) in polar coordinates
     */
    public static double atan2(double y, double x) {
        return java.lang.Math.atan2(y, x);
    }

    // Hyperbolic functions

    /**
     * Returns the hyperbolic sine of a value.
     * @param x the number whose hyperbolic sine is to be returned
     * @return the hyperbolic sine of x
     */
    public static double sinh(double x) {
        return java.lang.Math.sinh(x);
    }

    /**
     * Returns the hyperbolic cosine of a value.
     * @param x the number whose hyperbolic cosine is to be returned
     * @return the hyperbolic cosine of x
     */
    public static double cosh(double x) {
        return java.lang.Math.cosh(x);
    }

    /**
     * Returns the hyperbolic tangent of a value.
     * @param x the number whose hyperbolic tangent is to be returned
     * @return the hyperbolic tangent of x
     */
    public static double tanh(double x) {
        return java.lang.Math.tanh(x);
    }

    // Exponential and logarithmic functions

    /**
     * Returns Euler's number e raised to the power of a value.
     * @param x the exponent to raise e to
     * @return the value e^x
     */
    public static double exp(double x) {
        return java.lang.Math.exp(x);
    }

    /**
     * Returns the natural logarithm (base e) of a value.
     * @param x a value
     * @return the natural logarithm of x
     */
    public static double log(double x) {
        return java.lang.Math.log(x);
    }

    /**
     * Returns the base 10 logarithm of a value.
     * @param x a value
     * @return the base 10 logarithm of x
     */
    public static double log10(double x) {
        return java.lang.Math.log10(x);
    }

    /**
     * Returns the value of the first argument raised to the power of the second argument.
     * @param base the base
     * @param exp the exponent
     * @return the value base^exp
     */
    public static double pow(double base, double exp) {
        return java.lang.Math.pow(base, exp);
    }

    /**
     * Returns the correctly rounded positive square root of a value.
     * @param x a value
     * @return the positive square root of x
     */
    public static double sqrt(double x) {
        return java.lang.Math.sqrt(x);
    }

    /**
     * Returns the cube root of a value.
     * @param x a value
     * @return the cube root of x
     */
    public static double cbrt(double x) {
        return java.lang.Math.cbrt(x);
    }

    // Absolute value functions

    /**
     * Returns the absolute value of a double value.
     * @param x the argument whose absolute value is to be determined
     * @return the absolute value of the argument
     */
    public static double abs(double x) {
        return java.lang.Math.abs(x);
    }

    /**
     * Returns the absolute value of a float value.
     * @param x the argument whose absolute value is to be determined
     * @return the absolute value of the argument
     */
    public static float absFloat(float x) {
        return java.lang.Math.abs(x);
    }

    /**
     * Returns the absolute value of an int value.
     * @param x the argument whose absolute value is to be determined
     * @return the absolute value of the argument
     */
    public static int absInt(int x) {
        return java.lang.Math.abs(x);
    }

    /**
     * Returns the absolute value of a long value.
     * @param x the argument whose absolute value is to be determined
     * @return the absolute value of the argument
     */
    public static long absLong(long x) {
        return java.lang.Math.abs(x);
    }

    // Min/Max functions

    /**
     * Returns the smaller of two double values.
     * @param a an argument
     * @param b another argument
     * @return the smaller of a and b
     */
    public static double min(double a, double b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the smaller of two int values.
     * @param a an argument
     * @param b another argument
     * @return the smaller of a and b
     */
    public static int minInt(int a, int b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the smaller of two long values.
     * @param a an argument
     * @param b another argument
     * @return the smaller of a and b
     */
    public static long minLong(long a, long b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the greater of two double values.
     * @param a an argument
     * @param b another argument
     * @return the greater of a and b
     */
    public static double max(double a, double b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the greater of two int values.
     * @param a an argument
     * @param b another argument
     * @return the greater of a and b
     */
    public static int maxInt(int a, int b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Returns the greater of two long values.
     * @param a an argument
     * @param b another argument
     * @return the greater of a and b
     */
    public static long maxLong(long a, long b) {
        return java.lang.Math.max(a, b);
    }

    // Rounding functions

    /**
     * Returns the largest (closest to positive infinity) double value
     * that is less than or equal to the argument and is equal to a mathematical integer.
     * @param x a value
     * @return the largest double value that is less than or equal to x and is equal to an integer
     */
    public static double floor(double x) {
        return java.lang.Math.floor(x);
    }

    /**
     * Returns the smallest (closest to negative infinity) double value
     * that is greater than or equal to the argument and is equal to a mathematical integer.
     * @param x a value
     * @return the smallest double value that is greater than or equal to x and is equal to an integer
     */
    public static double ceil(double x) {
        return java.lang.Math.ceil(x);
    }

    /**
     * Returns the closest long to the argument, with ties rounding to positive infinity.
     * @param x a floating-point value to be rounded
     * @return the value of the argument rounded to the nearest long value
     */
    public static long round(double x) {
        return java.lang.Math.round(x);
    }

    /**
     * Returns the closest int to the argument, with ties rounding to positive infinity.
     * @param x a floating-point value to be rounded
     * @return the value of the argument rounded to the nearest int value
     */
    public static int roundFloat(float x) {
        return java.lang.Math.round(x);
    }

    // Random number generation

    /**
     * Returns a double value with a positive sign, greater than or equal to 0.0
     * and less than 1.0.
     * @return a pseudorandom double greater than or equal to 0.0 and less than 1.0
     */
    public static double random() {
        return java.lang.Math.random();
    }

    /**
     * Returns a random integer between min (inclusive) and max (inclusive).
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return a random integer in the range [min, max]
     */
    public static int randomInt(int min, int max) {
        return min + (int)(java.lang.Math.random() * (max - min + 1));
    }

    // Sign functions

    /**
     * Returns the signum function of the argument.
     * @param x the floating-point value whose signum is to be returned
     * @return the signum function of the argument (returns -1.0, 0.0, or 1.0)
     */
    public static double signum(double x) {
        return java.lang.Math.signum(x);
    }

    /**
     * Returns the signum function of the argument.
     * @param x the floating-point value whose signum is to be returned
     * @return the signum function of the argument (returns -1.0f, 0.0f, or 1.0f)
     */
    public static float signumFloat(float x) {
        return java.lang.Math.signum(x);
    }

    // Conversion functions

    /**
     * Converts an angle measured in degrees to an approximately equivalent angle measured in radians.
     * @param degrees an angle in degrees
     * @return the measurement of the angle in radians
     */
    public static double toRadians(double degrees) {
        return java.lang.Math.toRadians(degrees);
    }

    /**
     * Converts an angle measured in radians to an approximately equivalent angle measured in degrees.
     * @param radians an angle in radians
     * @return the measurement of the angle in degrees
     */
    public static double toDegrees(double radians) {
        return java.lang.Math.toDegrees(radians);
    }

    // Clamping (useful for games/graphics)

    /**
     * Clamps a value between a minimum and maximum.
     * @param value the value to clamp
     * @param min the minimum value
     * @param max the maximum value
     * @return the clamped value
     */
    public static double clamp(double value, double min, double max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    /**
     * Clamps an int value between a minimum and maximum.
     * @param value the value to clamp
     * @param min the minimum value
     * @param max the maximum value
     * @return the clamped value
     */
    public static int clampInt(int value, int min, int max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    // Hypotenuse

    /**
     * Returns sqrt(x^2 + y^2) without intermediate overflow or underflow.
     * @param x a value
     * @param y a value
     * @return sqrt(x^2 + y^2)
     */
    public static double hypot(double x, double y) {
        return java.lang.Math.hypot(x, y);
    }
}
