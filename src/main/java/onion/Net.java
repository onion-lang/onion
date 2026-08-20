package onion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP sockets.
 *
 * Onion could reach the network only as an HTTP client; there was no way to speak any
 * other protocol, and no way to accept a connection at all. This is the other half.
 *
 * Usage:
 *   val conn = Net::connect("example.com", 80)
 *   conn.writeLine("GET / HTTP/1.0")
 *   conn.writeLine("")
 *   IO::println(conn.readAll())
 *   conn.close()
 *
 *   val listener = Net::listen(0)          // 0 asks the OS for a free port
 *   IO::println("listening on " + listener.port())
 *   val client = listener.accept()
 *   client.writeLine("hello " + client.remoteAddress())
 *   client.close()
 *   listener.close()
 *
 * Every failure surfaces as an unchecked exception carrying the address that failed, so
 * `try`/`catch` in Onion reads naturally and a stack trace names the host rather than
 * just "connection refused".
 */
public final class Net {
    private Net() {} // Prevent instantiation

    /** Bytes read in one go when no length is known. */
    private static final int BUFFER = 8192;

    // ========== Client ==========

    /** Opens a connection, waiting as long as the OS default allows. */
    public static Conn connect(String host, int port) {
        return connect(host, port, 0);
    }

    /**
     * Opens a connection, giving up after {@code timeoutMillis}.
     *
     * @param timeoutMillis 0 waits for the OS default, which on a dropped packet can be
     *                      a minute or more — pass a real timeout in anything interactive.
     */
    public static Conn connect(String host, int port, int timeoutMillis) {
        requireHost(host);
        requirePort(port, "port");
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), Math.max(0, timeoutMillis));
            return new Conn(socket);
        } catch (IOException e) {
            closeQuietly(socket);
            throw new RuntimeException("Net: could not connect to " + host + ":" + port
                + " (" + e.getMessage() + ")", e);
        }
    }

    // ========== Server ==========

    /** Listens on every local address. Port 0 asks the OS for a free one. */
    public static Listener listen(int port) {
        return listen(null, port, 50);
    }

    /**
     * Listens on one address.
     *
     * @param host    the interface to bind, or null for every local address. Binding
     *                "localhost" is what keeps a test — or a development server — from
     *                being reachable from the rest of the network.
     * @param backlog how many pending connections the OS should queue.
     */
    public static Listener listen(String host, int port, int backlog) {
        requirePort(port, "port");
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            InetSocketAddress address = host == null
                ? new InetSocketAddress(port)
                : new InetSocketAddress(host, port);
            socket.bind(address, Math.max(1, backlog));
            return new Listener(socket);
        } catch (IOException e) {
            throw new RuntimeException("Net: could not listen on "
                + (host == null ? "*" : host) + ":" + port + " (" + e.getMessage() + ")", e);
        }
    }

    // ========== Types ==========

    /** One end of an open TCP connection. */
    public static final class Conn {
        private final Socket socket;
        private final BufferedReader reader;
        private final OutputStream output;

        Conn(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.output = socket.getOutputStream();
        }

        /** The next line, or null at end of stream. The line terminator is not included. */
        public String readLine() {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new RuntimeException("Net: read failed on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** Everything until the peer closes, decoded as UTF-8. */
        public String readAll() {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[BUFFER];
            try {
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    out.append(buffer, 0, read);
                }
            } catch (IOException e) {
                throw new RuntimeException("Net: read failed on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
            return out.toString();
        }

        /**
         * Everything until the peer closes, undecoded.
         *
         * A byte array rather than a List because this is the Java boundary — the same
         * place `Files::readBytes` sits — and a List of boxed bytes for a megabyte of
         * payload would be absurd.
         */
        public byte[] readBytes() {
            try {
                InputStream in = socket.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Net: read failed on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** Writes text as UTF-8 and flushes, so nothing sits in a buffer unsent. */
        public void write(String text) {
            try {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException("Net: write failed on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** Writes text followed by CRLF, which is what line-oriented protocols expect. */
        public void writeLine(String text) {
            write(text + "\r\n");
        }

        /** Writes raw bytes and flushes. */
        public void writeBytes(byte[] bytes) {
            try {
                output.write(bytes);
                output.flush();
            } catch (IOException e) {
                throw new RuntimeException("Net: write failed on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** Closes the write side while still reading, which some protocols need to signal EOF. */
        public void closeWrite() {
            try {
                socket.shutdownOutput();
            } catch (IOException e) {
                throw new RuntimeException("Net: could not half-close " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** How long a read may block before failing. 0 means forever. */
        public void timeout(int millis) {
            try {
                socket.setSoTimeout(Math.max(0, millis));
            } catch (IOException e) {
                throw new RuntimeException("Net: could not set a timeout on " + remoteAddress()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        public String remoteAddress() {
            java.net.SocketAddress remote = socket.getRemoteSocketAddress();
            return remote == null ? "<disconnected>" : remote.toString();
        }

        public boolean isClosed() {
            return socket.isClosed();
        }

        /** Idempotent, so a `finally` block never has to check first. */
        public void close() {
            closeQuietly(socket);
        }
    }

    /** A bound listening socket. */
    public static final class Listener {
        private final ServerSocket socket;

        Listener(ServerSocket socket) {
            this.socket = socket;
        }

        /** Waits for a connection. Blocks until one arrives or the listener is closed. */
        public Conn accept() {
            try {
                return new Conn(socket.accept());
            } catch (IOException e) {
                throw new RuntimeException("Net: accept failed on port " + port()
                    + " (" + e.getMessage() + ")", e);
            }
        }

        /** The port actually bound, which is the one to use after listening on 0. */
        public int port() {
            return socket.getLocalPort();
        }

        public boolean isClosed() {
            return socket.isClosed();
        }

        /** Idempotent. A blocked `accept` fails once this happens, which is how to stop one. */
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing twice, or closing an already-broken socket, is not a failure
                // worth making a caller handle.
            }
        }
    }

    // ========== Internals ==========

    private static void requireHost(String host) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Net: host must not be empty");
        }
    }

    private static void requirePort(int port, String what) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException(
                "Net: " + what + " must be between 0 and 65535, got " + port);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // See Listener.close.
        }
    }
}
