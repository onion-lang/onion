package onion.typeclass;

/**
 * The {@code Float} instance of {@link Numeric}: it adds in float precision.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code Float};
 * Java callers get it from {@link Numeric#forFloat()}.
 */
public final class Numeric$$Float implements Numeric<Float> {
    public Numeric$$Float() {
    }

    @Override
    public Float zero() { return 0.0f; }

    @Override
    public Float one() { return 1.0f; }

    @Override
    public Float plus(Float a, Float b) { return a + b; }

    @Override
    public Float times(Float a, Float b) { return a * b; }
}
