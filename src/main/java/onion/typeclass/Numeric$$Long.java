package onion.typeclass;

/**
 * The exact {@code Long} instance of {@link Numeric}: overflow throws {@link ArithmeticException}.
 * The compiler passes it for {@code [T: Numeric]} when {@code T} is {@code Long};
 * Java callers get it from {@link Numeric#forLong()}.
 */
public final class Numeric$$Long implements Numeric<Long> {
    public Numeric$$Long() {
    }

    @Override
    public Long zero() { return 0L; }

    @Override
    public Long one() { return 1L; }

    @Override
    public Long plus(Long a, Long b) { return Math.addExact(a, b); }

    @Override
    public Long times(Long a, Long b) { return Math.multiplyExact(a, b); }
}
