package onion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small HTTP server.
 *
 * Built on the JDK's own {@code com.sun.net.httpserver}, so this adds no dependency —
 * a language that can only make requests and never answer one is missing half of what
 * people write tools for.
 *
 * Usage:
 *   val server = Server::start(8080)
 *   server.handle("/hello") { req => Server::text("hi") }
 *   server.await()
 *
 * Routing by regex, which is what pairs with Onion's `re"…"` select patterns:
 *
 *   server.handleAll { req =>
 *     select req.path() {
 *       case re"/users/(\d+)" (id): Server::json("{\"id\":" + id + "}")
 *       case "/health":             Server::text("ok")
 *       else:                       Server::notFound()
 *     }
 *   }
 *
 * Port 0 asks the OS for a free port, which `port()` then reports — that is what makes a
 * server testable without picking a number and hoping.
 */
public final class Server {
    private Server() {} // Prevent instantiation

    /** What a handler receives. */
    public static final class Request {
        private final HttpExchange exchange;
        private final String body;

        Request(HttpExchange exchange, String body) {
            this.exchange = exchange;
            this.body = body;
        }

        public String method() { return exchange.getRequestMethod(); }

        /** The path only, without the query string. */
        public String path() { return exchange.getRequestURI().getPath(); }

        /** The raw query string, or "" when there is none. */
        public String query() {
            String raw = exchange.getRequestURI().getRawQuery();
            return raw == null ? "" : raw;
        }

        /** The decoded body, read in full before the handler runs. */
        public String body() { return body; }

        /** One header, case-insensitively, or null when absent. */
        public String header(String name) {
            return exchange.getRequestHeaders().getFirst(name);
        }

        /** Every header, as a name-to-value Map — not an array, per the stdlib's habit. */
        public Map headers() {
            Map<String, String> out = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) out.put(name, values.get(0));
            });
            return out;
        }

        /** Query parameters split out of {@link #query()}, in the order written. */
        public Map params() {
            Map<String, String> out = new LinkedHashMap<>();
            String raw = query();
            if (raw.isEmpty()) return out;
            for (String pair : raw.split("&")) {
                if (pair.isEmpty()) continue;
                int equals = pair.indexOf('=');
                String key = equals < 0 ? pair : pair.substring(0, equals);
                String value = equals < 0 ? "" : pair.substring(equals + 1);
                out.put(decode(key), decode(value));
            }
            return out;
        }

        private static String decode(String value) {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return "Server.Request(" + method() + " " + path() + ")";
        }
    }

    /** What a handler returns. Immutable: every wither hands back a new one. */
    public static final class Response {
        private final int status;
        private final String body;
        private final Map<String, String> headers;

        Response(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        public static Response of(int status, String body) {
            return new Response(status, body == null ? "" : body, new LinkedHashMap<>());
        }

        public int status() { return status; }
        public String body() { return body; }

        public Response withStatus(int status) {
            return new Response(status, body, new LinkedHashMap<>(headers));
        }

        public Response withHeader(String name, String value) {
            Map<String, String> next = new LinkedHashMap<>(headers);
            next.put(name, value);
            return new Response(status, body, next);
        }

        @Override
        public String toString() {
            return "Server.Response(" + status + ", " + body.length() + " bytes)";
        }
    }

    /** What a route does. A single method, so an Onion lambda converts to it directly. */
    public interface Handler {
        Response handle(Request request);
    }

    // ========== Responses ==========

    /** 200 text/plain. */
    public static Response text(String body) {
        return Response.of(200, body).withHeader("Content-Type", "text/plain; charset=utf-8");
    }

    /** 200 application/json. The body is passed through untouched. */
    public static Response json(String body) {
        return Response.of(200, body).withHeader("Content-Type", "application/json; charset=utf-8");
    }

    /** 200 text/html. */
    public static Response html(String body) {
        return Response.of(200, body).withHeader("Content-Type", "text/html; charset=utf-8");
    }

    public static Response notFound() {
        return Response.of(404, "Not Found")
            .withHeader("Content-Type", "text/plain; charset=utf-8");
    }

    public static Response status(int code, String body) {
        return Response.of(code, body).withHeader("Content-Type", "text/plain; charset=utf-8");
    }

    // ========== Lifecycle ==========

    /** Binds every local address. Port 0 asks the OS for a free one. */
    public static Instance start(int port) {
        return start(null, port);
    }

    /**
     * Binds one address.
     *
     * @param host the interface to bind, or null for every local address. Binding
     *             "localhost" is what keeps a development server off the network.
     */
    public static Instance start(String host, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(
                "Server: port must be between 0 and 65535, got " + port);
        }
        try {
            InetSocketAddress address = host == null
                ? new InetSocketAddress(port)
                : new InetSocketAddress(host, port);
            HttpServer server = HttpServer.create(address, 0);
            // A pool rather than the default (which runs handlers on the accept thread and
            // serialises every request), bounded so a flood cannot exhaust memory.
            //
            // Daemon threads, deliberately: a non-daemon pool keeps the JVM alive after
            // `main` returns, so a script that forgets to call `stop()` never exits. That
            // is not a hypothetical -- it hung the first sample written against this API.
            ExecutorService pool = Executors.newFixedThreadPool(8, runnable -> {
                Thread thread = new Thread(runnable, "onion-server");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(pool);
            server.start();
            return new Instance(server, pool);
        } catch (IOException e) {
            throw new RuntimeException("Server: could not bind "
                + (host == null ? "*" : host) + ":" + port + " (" + e.getMessage() + ")", e);
        }
    }

    /** A running server. */
    public static final class Instance {
        private final HttpServer server;
        private final ExecutorService pool;
        private final List<Route> routes = new ArrayList<>();
        private volatile Handler fallback;

        Instance(HttpServer server, ExecutorService pool) {
            this.server = server;
            this.pool = pool;
            server.createContext("/", this::dispatch);
        }

        /** Routes one exact path. Returns this, so calls chain. */
        public Instance handle(String path, Handler handler) {
            if (path == null || !path.startsWith("/")) {
                throw new IllegalArgumentException(
                    "Server: a route path must start with '/', got " + path);
            }
            require(handler);
            routes.add(new Route(path, handler));
            return this;
        }

        /**
         * Routes everything not matched by {@link #handle}. This is the hook for doing the
         * routing in Onion — `select` over `request.path()` with `re"…"` patterns — rather
         * than registering paths one at a time.
         */
        public Instance handleAll(Handler handler) {
            require(handler);
            this.fallback = handler;
            return this;
        }

        /** The port actually bound, which is the one to use after starting on 0. */
        public int port() {
            return server.getAddress().getPort();
        }

        /** Blocks until the process ends. What a `main` calls after wiring the routes. */
        public void await() {
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Stops accepting and waits up to a second for handlers in flight.
         *
         * The pool is shut down too. `HttpServer.stop` does not touch an executor it was
         * given, so leaving it running would keep threads (and whatever they hold) alive
         * for the rest of the process.
         */
        public void stop() {
            server.stop(1);
            pool.shutdownNow();
        }

        private void require(Handler handler) {
            if (handler == null) throw new IllegalArgumentException("Server: handler must not be null");
        }

        private void dispatch(HttpExchange exchange) throws IOException {
            Response response;
            try {
                Request request = new Request(exchange, readBody(exchange));
                Handler handler = route(request.path());
                response = handler == null ? notFound() : handler.handle(request);
                if (response == null) response = notFound();
            } catch (RuntimeException e) {
                // A handler that throws must not take the server down or leave the client
                // hanging on a socket that never answers.
                response = status(500, "Internal Server Error");
                e.printStackTrace();
            }
            send(exchange, response);
        }

        private Handler route(String path) {
            for (Route route : routes) {
                if (route.path.equals(path)) return route.handler;
            }
            return fallback;
        }

        private static String readBody(HttpExchange exchange) throws IOException {
            try (InputStream in = exchange.getRequestBody()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static void send(HttpExchange exchange, Response response) throws IOException {
            byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
            response.headers.forEach((name, value) ->
                exchange.getResponseHeaders().set(name, value));
            // A 204 or 304 must not carry a body length at all; -1 is how this API says so.
            boolean bodiless = response.status == 204 || response.status == 304;
            exchange.sendResponseHeaders(response.status, bodiless ? -1 : body.length);
            if (!bodiless) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.close();
            }
        }
    }

    private static final class Route {
        final String path;
        final Handler handler;

        Route(String path, Handler handler) {
            this.path = path;
            this.handler = handler;
        }
    }
}
