package onion;

import java.util.ArrayList;
import java.util.List;

/** {@link Shape#sepBy}: repetition with a literal separator. */
final class SepByShape<T> implements Shape<List<T>> {

    private final Shape<T> element;
    private final String separator;

    SepByShape(Shape<T> element, String separator) {
        this.element = element;
        this.separator = separator;
    }

    @Override
    public Outcome<List<T>> parse(String text, Origin origin) {
        List<Outcome<T>> each = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            // Literal split, not a pattern: see Shape.sepBy for why.
            for (String part : text.split(java.util.regex.Pattern.quote(separator), -1)) {
                each.add(element.parse(part, origin));
            }
        }
        return Outcome.all(each);
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
            if (sb.length() > 0) sb.append(separator);
            sb.append(rendered);
            i++;
        }
        return sb.toString();
    }

    /**
     * An element rendering that contains the separator is indistinguishable from a real
     * boundary on reparse, so printing it would silently split one element into two
     * instead of merely failing L1. Refuse rather than misrender.
     */
    private void requireRenderable(int index, String rendered) {
        if (rendered.contains(separator)) {
            throw new IllegalArgumentException(
                "element at index " + index + " contains the separator '" + separator
                    + "', which this sepBy shape cannot represent: " + rendered);
        }
    }

    @Override
    public String describe() {
        return element.describe() + " separated by '" + separator + "'";
    }
}
