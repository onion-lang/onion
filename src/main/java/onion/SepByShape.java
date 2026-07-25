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
        for (T v : values) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(element.print(v));
        }
        return sb.toString();
    }

    @Override
    public String describe() {
        return element.describe() + " separated by '" + separator + "'";
    }
}
