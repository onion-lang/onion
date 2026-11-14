package onion.typeclass;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The built-in numeric type class: the operations a generic sum or product needs.
 *
 * <p>An Onion method takes it as a context bound and calls it through the
 * dictionary, after {@code import { onion.typeclass.Numeric }}:
 *
 * <pre>
 * def total[T: Numeric](xs: List[T]): T {
 *   var acc = Numeric[T]::zero()
 *   foreach x in xs { acc = Numeric[T]::plus(acc, x) }
 *   return acc
 * }
 * total([1, 2, 3])   // 6, an Int
 * </pre>
 *
 * <p>Instances ship for {@code Int}, {@code Long}, {@code Double}, {@code Float},
 * {@code BigInteger} and {@code BigDecimal}. {@code Int} and {@code Long} are exact:
 * they throw {@link ArithmeticException} on overflow instead of wrapping. A user adds
 * an instance for another type with {@code instance Numeric[Short] { ... }}.
 *
 * <p>The shape is frozen (RFC #2760): {@code zero}, {@code one}, {@code plus} and
 * {@code times}. A Java caller passes an instance explicitly, from the named
 * factories below ({@code Numeric.forInt()}, ...).
 */
public interface Numeric<T> {
    /** The additive identity: the sum of an empty list. */
    T zero();

    /** The multiplicative identity: the product of an empty list. */
    T one();

    /** {@code a + b}. */
    T plus(T a, T b);

    /** {@code a * b}. */
    T times(T a, T b);

    /** The exact {@code Int} instance (overflow throws). */
    static Numeric<Integer> forInt() { return new Numeric$$Integer(); }

    /** The exact {@code Long} instance (overflow throws). */
    static Numeric<Long> forLong() { return new Numeric$$Long(); }

    /** The {@code Double} instance. */
    static Numeric<Double> forDouble() { return new Numeric$$Double(); }

    /** The {@code Float} instance; it adds in float precision. */
    static Numeric<Float> forFloat() { return new Numeric$$Float(); }

    /** The {@code BigInteger} instance. */
    static Numeric<BigInteger> forBigInteger() { return new Numeric$$BigInteger(); }

    /** The {@code BigDecimal} instance; it adds without rounding. */
    static Numeric<BigDecimal> forBigDecimal() { return new Numeric$$BigDecimal(); }
}
