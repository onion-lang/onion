package onion;

/** {@link Shape#xmap}: the same boundary, seen as a different type. */
final class XmapShape<T, U> implements Shape<U> {

    private final Shape<T> underlying;
    private final Function1<T, U> forward;
    private final Function1<U, T> backward;

    XmapShape(Shape<T> underlying, Function1<T, U> forward, Function1<U, T> backward) {
        this.underlying = underlying;
        this.forward = forward;
        this.backward = backward;
    }

    @Override
    public Outcome<U> parse(String text, Origin origin) {
        return underlying.parse(text, origin).map(forward);
    }

    @Override
    public boolean canPrint() {
        return underlying.canPrint();
    }

    @Override
    public String print(U value) {
        return underlying.print(backward.call(value));
    }

    @Override
    public String describe() {
        return underlying.describe();
    }
}
