package onion.typeclass;

/**
 * The exact {@code Int} instance of {@link Numeric}: overflow throws {@link ArithmeticException}.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code Int};
 * Java callers get it from {@link Numeric#forInt()}.
 */
public final class Numeric$$Integer implements Numeric<Integer> {
    public Numeric$$Integer() {
    }

    @Override
    public Integer zero() { return 0; }

    @Override
    public Integer one() { return 1; }

    @Override
    public Integer plus(Integer a, Integer b) { return Math.addExact(a, b); }

    @Override
    public Integer times(Integer a, Integer b) { return Math.multiplyExact(a, b); }
}
