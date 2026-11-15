package onion.typeclass;

import java.math.BigDecimal;

/**
 * The {@code BigDecimal} instance of {@link Numeric}: it adds and multiplies without rounding.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code BigDecimal};
 * Java callers get it from {@link Numeric#forBigDecimal()}.
 */
public final class Numeric$$BigDecimal implements Numeric<BigDecimal> {
    public Numeric$$BigDecimal() {
    }

    @Override
    public BigDecimal zero() { return BigDecimal.ZERO; }

    @Override
    public BigDecimal one() { return BigDecimal.ONE; }

    @Override
    public BigDecimal plus(BigDecimal a, BigDecimal b) { return a.add(b); }

    @Override
    public BigDecimal times(BigDecimal a, BigDecimal b) { return a.multiply(b); }
}
