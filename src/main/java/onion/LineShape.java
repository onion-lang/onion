package onion;

import java.util.ArrayList;
import java.util.List;

/** {@link Shape#lines()}: one value per line, all or nothing. */
final class LineShape<T> implements Shape<List<T>> {

    private final Shape<T> element;

    LineShape(Shape<T> element) {
        this.element = element;
    }

    @Override
    public Outcome<List<T>> parse(String text, Origin origin) {
        return Outcome.all(element.eachLine(text, origin));
    }

    @Override
    public boolean canPrint() {
        return element.canPrint();
    }

    @Override
    public String print(List<T> values) {
        StringBuilder sb = new StringBuilder();
        for (T v : values) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(element.print(v));
        }
        return sb.toString();
    }

    @Override
    public String describe() {
        return element.describe() + " per line";
    }
}
