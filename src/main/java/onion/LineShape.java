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
        int i = 0;
        for (T v : values) {
            String rendered = element.print(v);
            requireRenderable(i, rendered);
            if (sb.length() > 0) sb.append('\n');
            sb.append(rendered);
            i++;
        }
        return sb.toString();
    }

    /**
     * One value per line runs to the end of the line, so an element whose rendering
     * contains a line break cannot be represented at all: printing it would silently
     * split into an extra line indistinguishable from a real element, corrupting the
     * document instead of merely failing L1. Refuse rather than misrender.
     */
    private static void requireRenderable(int index, String rendered) {
        if (rendered.indexOf('\n') >= 0 || rendered.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                "element at index " + index + " contains a line break, which the "
                    + "one-per-line format cannot represent: " + rendered);
        }
    }

    @Override
    public String describe() {
        return element.describe() + " per line";
    }
}
