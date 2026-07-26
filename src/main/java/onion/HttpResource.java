package onion;

import java.util.ArrayList;
import java.util.List;

/**
 * A URL as a typed resource. Created by the {@code http"url"} literal (or
 * {@code http(url)}); the verb methods wrap {@link Http} so a fetch-and-parse
 * pipeline is one expression:
 *
 * <pre>
 * val body  = http"https://example.com/health".get
 * val data  = http"https://api.example.com/users".getJson
 * val reply = http"https://api.example.com/jobs".postJson("{\"id\":1}")
 * </pre>
 */
public final class HttpResource {
    private final String url;

    public HttpResource(String url) {
        this.url = url;
    }

    /** The underlying URL string. */
    public String url() {
        return url;
    }

    /** GET, returning the response body. */
    public String get() throws Exception {
        return Http.get(url);
    }

    /** GET with headers (alternating names and values). */
    public String get(java.util.List headers) throws Exception {
        return Http.get(url, headers);
    }

    /** GET and parse the body as JSON. */
    public Object getJson() throws Exception {
        return Json.parse(Http.get(url));
    }

    /**
     * The response body read through {@code shape}.
     *
     * <p>Carries the URL into every defect, so a failure says which endpoint returned
     * something unexpected (issue #353). Named `read` rather than `as`, which is Onion's cast keyword. A transport failure is a defect too.
     */
    public <T> Outcome<T> read(Shape<T> shape) {
        String body;
        try {
            body = get();
        } catch (Exception e) {
            return Outcome.bad(Defect.at(Origin.atLine(url, 1), "", "a successful response", describe(e)));
        }
        return shape.parse(body, Origin.atLine(url, 1));
    }

    /**
     * One value per line of the response body, keeping both the lines that read and the
     * reasons the rest did not -- see {@link Shape#eachLine}. Each defect is positioned on
     * its line of the response. A transport failure is a single defect, not an exception.
     */
    public <T> List<Outcome<T>> eachLine(Shape<T> shape) {
        String body;
        try {
            body = get();
        } catch (Exception e) {
            List<Outcome<T>> failed = new ArrayList<>();
            failed.add(Outcome.bad(Defect.at(Origin.atLine(url, 1), "", "a successful response", describe(e))));
            return failed;
        }
        return shape.eachLine(body, Origin.atLine(url, 1));
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }

    /** POST a body, returning the response body. */
    public String post(String body) throws Exception {
        return Http.post(url, body);
    }

    /** POST a JSON body (Content-Type: application/json). */
    public String postJson(String jsonBody) throws Exception {
        return Http.postJson(url, jsonBody);
    }

    /** PUT a body. */
    public String put(String body) throws Exception {
        return Http.put(url, body);
    }

    /** DELETE. */
    public String delete() throws Exception {
        return Http.delete(url);
    }

    @Override
    public String toString() {
        return "http\"" + url + "\"";
    }
}
