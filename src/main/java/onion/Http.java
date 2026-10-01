package onion;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client utilities for Onion programs.
 * All methods are static; call them qualified, e.g. {@code Http::get(url)}
 * (Http is not in the default static import set).
 * For a request that needs its own method, headers, body or timeout and must report
 * the status, build it with {@code Http::request(method, url)} (see {@link Request}).
 */
public final class Http {
    private Http() {} // Prevent instantiation

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ========== Simple GET ==========

    /**
     * Performs a GET request and returns the response body.
     */
    public static String get(String url) throws Exception {
        if (url == null) return "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Performs a GET request with custom headers.
     * Headers are provided as alternating key-value pairs: ["Header1", "Value1", "Header2", "Value2"]
     */
    public static String get(String url, List headers) throws Exception {
        if (url == null) return "";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        addHeaders(builder, headers);
        java.net.http.HttpResponse<String> response = client.send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // ========== Simple POST ==========

    /**
     * Performs a POST request with the given body.
     */
    public static String post(String url, String body) throws Exception {
        if (url == null) return "";
        if (body == null) body = "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Performs a POST request with JSON body.
     */
    public static String postJson(String url, String jsonBody) throws Exception {
        if (url == null) return "";
        if (jsonBody == null) jsonBody = "{}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Performs a POST request with custom headers.
     */
    public static String post(String url, String body, List headers) throws Exception {
        if (url == null) return "";
        if (body == null) body = "";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addHeaders(builder, headers);
        java.net.http.HttpResponse<String> response = client.send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // ========== Response with Status ==========

    /**
     * Response object containing status code, body, and headers.
     */
    public static class Response {
        public final int status;
        public final String body;
        public final List<String> headers;

        public Response(int status, String body, List<String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        public boolean isOk() {
            return status >= 200 && status < 300;
        }

        public boolean isError() {
            return status >= 400;
        }

        /**
         * The first value of the named response header, compared case-insensitively,
         * or null when the response has no such header.
         */
        public String header(String name) {
            if (name == null || headers == null) return null;
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                if (name.equalsIgnoreCase(headers.get(i))) return headers.get(i + 1);
            }
            return null;
        }
    }

    /**
     * Performs a GET request and returns a Response object.
     */
    public static Response getResponse(String url) throws Exception {
        if (url == null) return new Response(0, "", new ArrayList<String>());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return toResponse(response);
    }

    /**
     * Performs a POST request and returns a Response object.
     */
    public static Response postResponse(String url, String body) throws Exception {
        if (url == null) return new Response(0, "", new ArrayList<String>());
        if (body == null) body = "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return toResponse(response);
    }

    /**
     * Performs a GET request with custom headers and returns a Response object.
     * Headers are alternating names and values, as for {@link #get(String, List)}.
     */
    public static Response getResponse(String url, List headers) throws Exception {
        if (url == null) return new Response(0, "", new ArrayList<String>());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        addHeaders(builder, headers);
        return toResponse(client.send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()));
    }

    /**
     * Performs a POST request with custom headers and returns a Response object.
     * Headers are alternating names and values, as for {@link #post(String, String, List)}.
     */
    public static Response postResponse(String url, String body, List headers) throws Exception {
        if (url == null) return new Response(0, "", new ArrayList<String>());
        if (body == null) body = "";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addHeaders(builder, headers);
        return toResponse(client.send(builder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()));
    }

    // ========== Request Builder ==========

    /**
     * Starts building a request with any method, headers, body and timeout:
     * <pre>
     * val res = Http::request("POST", url)
     *   .header("content-type", "application/json")
     *   .body(json)
     *   .timeoutSeconds(120)
     *   .send()                    // Result[Http.Response, Http.HttpFailure]
     * </pre>
     * Building is effect-free; only {@link Request#send()} (and
     * {@link Request#sendOrThrow()}) touches the network.
     * The method is sent as written (HTTP methods are case-sensitive; use upper case).
     *
     * @throws IllegalArgumentException if the method is not a valid HTTP method token,
     *         or the URL is not an absolute http/https URL
     */
    public static Request request(String method, String url) {
        if (method == null || method.isEmpty()) {
            throw new IllegalArgumentException("Http::request: method is null or empty");
        }
        if (url == null) {
            throw new IllegalArgumentException("Http::request: url is null");
        }
        // Validate now, so a bad method or URL fails where it was written, not at send().
        HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody());
        return new Request(method, url, new ArrayList<String>(), null, 0L);
    }

    /**
     * An immutable HTTP request description, made by {@link Http#request(String, String)}.
     * Every builder method returns a new Request and leaves the receiver unchanged, so
     * a base request (say, with authentication headers) can be shared and extended.
     */
    public static final class Request {
        private final String method;
        private final String url;
        private final List<String> headers; // alternating names and values
        private final String body;          // null: no body
        private final long timeoutMillis;   // 0: no per-request timeout

        private Request(String method, String url, List<String> headers, String body, long timeoutMillis) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            this.timeoutMillis = timeoutMillis;
        }

        /** The HTTP method, as given to {@code Http::request}. */
        public String method() {
            return method;
        }

        /** The request URL. */
        public String url() {
            return url;
        }

        /**
         * Adds a header. A name given twice is sent twice (nothing is replaced).
         *
         * @throws IllegalArgumentException if the name or value is null, or the JDK
         *         HTTP client does not allow setting that header (e.g. Host, Content-Length)
         */
        public Request header(String name, String value) {
            if (name == null) throw new IllegalArgumentException("Http.Request.header: name is null");
            if (value == null) {
                throw new IllegalArgumentException("Http.Request.header: value of '" + name + "' is null");
            }
            HttpRequest.newBuilder().header(name, value); // validates name and value
            List<String> next = new ArrayList<>(headers);
            next.add(name);
            next.add(value);
            return new Request(method, url, next, body, timeoutMillis);
        }

        /**
         * Adds headers given as alternating names and values: {@code ["Name1", "Value1", ...]}.
         *
         * @throws IllegalArgumentException if the list has an odd length or holds a null
         */
        public Request headers(List pairs) {
            if (pairs == null) return this;
            if (pairs.size() % 2 != 0) {
                throw new IllegalArgumentException(
                        "Http.Request.headers: expected alternating names and values, got " + pairs.size() + " elements");
            }
            Request r = this;
            for (int i = 0; i < pairs.size(); i += 2) {
                Object name = pairs.get(i);
                Object value = pairs.get(i + 1);
                r = r.header(name == null ? null : name.toString(), value == null ? null : value.toString());
            }
            return r;
        }

        /** Sets the request body (sent as UTF-8). {@code null} means no body. */
        public Request body(String body) {
            return new Request(method, url, headers, body, timeoutMillis);
        }

        /**
         * Sets a per-request timeout: if no response arrives in time, {@link #send()}
         * returns an {@code Err} whose {@link HttpFailure#kind()} is {@code "timeout"}.
         * Without one, a request waits as long as the server takes (connecting is always
         * bounded at 30 seconds).
         *
         * @throws IllegalArgumentException if seconds is not positive
         */
        public Request timeoutSeconds(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("Http.Request.timeoutSeconds: must be positive, got " + seconds);
            }
            return new Request(method, url, headers, body, seconds * 1000L);
        }

        /**
         * Sets a per-request timeout in milliseconds; see {@link #timeoutSeconds(int)}.
         *
         * @throws IllegalArgumentException if millis is not positive
         */
        public Request timeoutMillis(long millis) {
            if (millis <= 0) {
                throw new IllegalArgumentException("Http.Request.timeoutMillis: must be positive, got " + millis);
            }
            return new Request(method, url, headers, body, millis);
        }

        /**
         * Sends the request. Any HTTP response, whatever its status, is
         * {@code Ok(response)}: a 4xx or 5xx is a Response like any other (check
         * {@code status}, {@code isOk()} or {@code isError()}). Not getting a response at
         * all is {@code Err(failure)}, an {@link HttpFailure} saying why: {@code "timeout"},
         * {@code "connect"} or {@code "io"}. So "the server said no" and "the server was
         * never reached" are distinct in the type, and neither one throws.
         *
         * @throws InterruptedException if the calling thread is interrupted while waiting
         */
        public Result<Response, HttpFailure> send() throws InterruptedException {
            try {
                return Result.ok(toResponse(client.send(build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString())));
            } catch (java.io.IOException e) {
                return Result.err(HttpFailure.of(method, url, e));
            }
        }

        /**
         * Sends the request and returns the response, whatever its status, or throws the
         * underlying exception when no response arrives: {@code HttpTimeoutException}
         * for a timeout, another {@code java.io.IOException} for a connection failure.
         * For scripts where "no response" should simply stop the program. Unlike
         * {@code send().getOrThrow()}, it rethrows the original exception (its type and
         * cause) instead of wrapping the {@link HttpFailure}'s text.
         */
        public Response sendOrThrow() throws java.io.IOException, InterruptedException {
            return toResponse(client.send(build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()));
        }

        private HttpRequest build() {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            for (int i = 0; i + 1 < headers.size(); i += 2) {
                builder.header(headers.get(i), headers.get(i + 1));
            }
            if (timeoutMillis > 0) builder.timeout(Duration.ofMillis(timeoutMillis));
            return builder.build();
        }

        @Override
        public String toString() {
            return method + " " + url;
        }
    }

    /**
     * Why {@link Request#send()} got no response at all (as opposed to an HTTP error
     * status, which is an ordinary {@link Response}). A plain value: it is returned in
     * an {@code Err}, never thrown.
     *
     * <p>{@code kind} is one of three strings, so it can be matched with {@code select}:
     * <ul>
     *   <li>{@code "timeout"}: the per-request timeout elapsed before a response arrived</li>
     *   <li>{@code "connect"}: no connection was made (refused, unknown host, unreachable,
     *       the 30-second connect timeout, a failed TLS handshake)</li>
     *   <li>{@code "io"}: the connection broke while sending or receiving</li>
     * </ul>
     *
     * @param kind    {@code "timeout"}, {@code "connect"} or {@code "io"}
     * @param method  the request's HTTP method
     * @param url     the request's URL
     * @param message a human-readable reason (never null)
     * @param cause   the underlying exception
     */
    public record HttpFailure(String kind, String method, String url, String message, Throwable cause) {

        /** True when the per-request timeout elapsed. */
        public boolean isTimeout() {
            return "timeout".equals(kind);
        }

        /** True when no connection could be made. */
        public boolean isConnect() {
            return "connect".equals(kind);
        }

        @Override
        public String toString() {
            return kind + ": " + method + " " + url + ": " + message;
        }

        static HttpFailure of(String method, String url, java.io.IOException e) {
            String kind = classify(e);
            return new HttpFailure(kind, method, url, describe(e, kind), e);
        }

        private static String classify(Throwable e) {
            // The connect timeout is a subclass of the request timeout, so check it first.
            if (e instanceof java.net.http.HttpConnectTimeoutException) return "connect";
            if (e instanceof java.net.http.HttpTimeoutException) return "timeout";
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof java.net.ConnectException
                        || t instanceof java.net.UnknownHostException
                        || t instanceof java.net.NoRouteToHostException
                        || t instanceof java.nio.channels.UnresolvedAddressException
                        || t instanceof javax.net.ssl.SSLHandshakeException) {
                    return "connect";
                }
                if (t.getCause() == t) break;
            }
            return "io";
        }

        /** The first message in the cause chain; the JDK often leaves ConnectException's empty. */
        private static String describe(Throwable e, String kind) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                String m = t.getMessage();
                if (m != null && !m.isEmpty()) return m;
                if (t.getCause() == t) break;
            }
            String what = switch (kind) {
                case "timeout" -> "timed out";
                case "connect" -> "could not connect";
                default -> "I/O error";
            };
            return what + " (" + e.getClass().getSimpleName() + ")";
        }
    }

    // ========== Other Methods ==========

    /**
     * Performs a PUT request.
     */
    public static String put(String url, String body) throws Exception {
        if (url == null) return "";
        if (body == null) body = "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Performs a DELETE request.
     */
    public static String delete(String url) throws Exception {
        if (url == null) return "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // ========== URL Utilities ==========

    /**
     * URL-encodes the given value.
     */
    public static String encodeUrl(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * URL-decodes the given value.
     */
    public static String decodeUrl(String value) {
        if (value == null) return "";
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Builds a query string from alternating key-value pairs.
     * Example: buildQuery(["name", "John", "age", "30"]) returns "name=John&age=30"
     */
    private static String buildQueryArray(String[] params) {
        if (params == null || params.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < params.length; i += 2) {
            if (sb.length() > 0) sb.append("&");
            sb.append(encodeUrl(params[i]));
            sb.append("=");
            sb.append(encodeUrl(params[i + 1]));
        }
        return sb.toString();
    }

    /**
     * Builds a query string from a list of alternating key-value pairs.
     */
    @SuppressWarnings("unchecked")
    public static String buildQuery(List params) {
        if (params == null || params.isEmpty()) return "";
        return buildQueryArray(toStringArray(params));
    }

    /**
     * Builds a full URL with query parameters.
     */
    @SuppressWarnings("unchecked")
    public static String buildUrl(String baseUrl, List params) {
        if (baseUrl == null) return "";
        String query = buildQuery(params);
        if (query.isEmpty()) return baseUrl;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + query;
    }

    // ========== Helpers ==========

    private static void addHeaders(HttpRequest.Builder builder, List headers) {
        if (headers == null) return;
        for (int i = 0; i + 1 < headers.size(); i += 2) {
            Object name = headers.get(i);
            Object value = headers.get(i + 1);
            if (name != null && value != null) {
                builder.header(name.toString(), value.toString());
            }
        }
    }

    /** Flattens a list of alternating keys and values for the array-based helpers. */
    private static String[] toStringArray(List params) {
        String[] arr = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Object o = params.get(i);
            arr[i] = o != null ? o.toString() : "";
        }
        return arr;
    }

    private static Response toResponse(java.net.http.HttpResponse<String> response) {
        List<String> headerList = new ArrayList<>();
        response.headers().map().forEach((key, values) -> {
            for (String value : values) {
                headerList.add(key);
                headerList.add(value);
            }
        });
        return new Response(
                response.statusCode(),
                response.body(),
                headerList
        );
    }
}
