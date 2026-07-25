package onion;

import java.util.ArrayList;
import java.util.List;

/** {@link Shape#orElse}: the first spelling that reads, or every reason none did. */
final class OrElseShape<T> implements Shape<T> {

    private final Shape<T> first;
    private final Shape<T> second;

    OrElseShape(Shape<T> first, Shape<T> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Outcome<T> parse(String text, Origin origin) {
        Outcome<T> a = first.parse(text, origin);
        if (a.isOk()) return a;
        Outcome<T> b = second.parse(text, origin);
        if (b.isOk()) return b;
        // Report both: the user is being told which spellings were tried, and hiding one
        // of them helps nobody.
        List<Defect> both = new ArrayList<>(a.defects());
        both.addAll(b.defects());
        return Outcome.bad(both);
    }

    @Override
    public boolean canPrint() {
        return first.canPrint();
    }

    @Override
    public String print(T value) {
        return first.print(value);
    }

    @Override
    public String describe() {
        return first.describe() + " or " + second.describe();
    }
}
