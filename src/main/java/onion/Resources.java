package onion;

import java.util.regex.Pattern;

/**
 * Entry points behind Onion's scheme-prefixed string literals. The literal
 * {@code file"x"} desugars to the unqualified call {@code file("x")}, which
 * resolves here through the default static imports — so the literal and the
 * function form are exactly equivalent, and dynamic values use the function:
 *
 * <pre>
 * val a = file"config.json"        // literal
 * val b = file(pathVariable)       // same thing, dynamic
 * val p = re"\d+"                  // compiled java.util.regex.Pattern
 * </pre>
 */
public final class Resources {
    private Resources() {
    }

    /** A file resource for the given path. */
    public static FileResource file(String path) {
        return new FileResource(path);
    }

    /** An HTTP resource for the given URL. */
    public static HttpResource http(String url) {
        return new HttpResource(url);
    }

    /** A compiled regular expression. */
    public static Pattern re(String pattern) {
        return Pattern.compile(pattern);
    }
}
