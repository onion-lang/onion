package onion.typeclass;

/**
 * The {@code Double} instance of {@link Numeric}.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code Double};
 * Java callers get it from {@link Numeric#forDouble()}.
 */
public final class Numeric$$Double implements Numeric<Double> {
    public Numeric$$Double() {
    }

    @Override
    public Double zero() { return 0.0; }

    @Override
    public Double one() { return 1.0; }

    @Override
    public Double plus(Double a, Double b) { return a + b; }

    @Override
    public Double times(Double a, Double b) { return a * b; }
}
