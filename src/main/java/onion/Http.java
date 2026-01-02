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
 * All methods are static and can be used without import.
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
    public static String get(String url, String[] headers) throws Exception {
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
    public static String post(String url, String body, String[] headers) throws Exception {
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
        public final String[] headers;

        public Response(int status, String body, String[] headers) {
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
    }

    /**
     * Performs a GET request and returns a Response object.
     */
    public static Response getResponse(String url) throws Exception {
        if (url == null) return new Response(0, "", new String[0]);
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
        if (url == null) return new Response(0, "", new String[0]);
        if (body == null) body = "";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return toResponse(response);
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
    public static String buildQuery(String[] params) {
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
        String[] arr = new String[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Object o = params.get(i);
            arr[i] = o != null ? o.toString() : "";
        }
        return buildQuery(arr);
    }

    /**
     * Builds a full URL with query parameters.
     */
    public static String buildUrl(String baseUrl, String[] params) {
        if (baseUrl == null) return "";
        String query = buildQuery(params);
        if (query.isEmpty()) return baseUrl;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + query;
    }

    /**
     * Builds a full URL with query parameters from a list.
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

    private static void addHeaders(HttpRequest.Builder builder, String[] headers) {
        if (headers == null) return;
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i] != null && headers[i + 1] != null) {
                builder.header(headers[i], headers[i + 1]);
            }
        }
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
                headerList.toArray(new String[0])
        );
    }
}
