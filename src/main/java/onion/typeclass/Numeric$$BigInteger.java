package onion.typeclass;

import java.math.BigInteger;

/**
 * The {@code BigInteger} instance of {@link Numeric}.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code BigInteger};
 * Java callers get it from {@link Numeric#forBigInteger()}.
 */
public final class Numeric$$BigInteger implements Numeric<BigInteger> {
    public Numeric$$BigInteger() {
    }

    @Override
    public BigInteger zero() { return BigInteger.ZERO; }

    @Override
    public BigInteger one() { return BigInteger.ONE; }

    @Override
    public BigInteger plus(BigInteger a, BigInteger b) { return a.add(b); }

    @Override
    public BigInteger times(BigInteger a, BigInteger b) { return a.multiply(b); }
}
