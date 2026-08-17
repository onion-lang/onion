package onion;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * SQL databases, over JDBC.
 *
 * The driver is not bundled — it is whatever the project declares:
 *
 *   [dependencies]
 *   "org.postgresql:postgresql" = "42.7.3"
 *
 * Usage:
 *   val db = Db::connect("jdbc:postgresql://localhost/app", "user", "secret")
 *   val rows = db.query("SELECT id, name FROM users WHERE age > ?", 18)
 *   foreach row: Map in rows { IO::println(row["name"]) }
 *   db.close()
 *
 * Values are always <em>bound</em>, never pasted into the SQL. `db.query("… WHERE name =
 * ?", name)` is safe with any name; building that string by concatenation is how SQL
 * injection happens, and this API gives no way to do it by accident.
 */
public final class Db {
    private Db() {} // Prevent instantiation

    /** What a transaction body is. One method, so an Onion lambda converts to it. */
    public interface Work {
        void run(Conn conn);
    }

    // ========== Connecting ==========

    public static Conn connect(String url) {
        return connect(url, null, null);
    }

    public static Conn connect(String url, String user, String password) {
        if (url == null || !url.startsWith("jdbc:")) {
            throw new IllegalArgumentException(
                "Db: a JDBC URL must start with \"jdbc:\", got " + url);
        }
        Properties properties = new Properties();
        if (user != null) properties.setProperty("user", user);
        if (password != null) properties.setProperty("password", password);

        try {
            return new Conn(open(url, properties), url);
        } catch (SQLException e) {
            throw new RuntimeException("Db: could not connect to " + url
                + " (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Opens a connection, falling back past {@link DriverManager} when it comes up empty.
     *
     * DriverManager only offers drivers its *caller* can see. A driver pulled in through
     * a project's `[dependencies]` is loaded by Onion's own classpath loader, not the one
     * that loaded this class, so DriverManager reports "No suitable driver" for a driver
     * that is demonstrably present — the exact case this class exists to serve. The
     * fallback asks the context classloader directly.
     */
    private static Connection open(String url, Properties properties) throws SQLException {
        try {
            return DriverManager.getConnection(url, properties);
        } catch (SQLException first) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) throw first;
            for (Driver driver : ServiceLoader.load(Driver.class, loader)) {
                try {
                    if (!driver.acceptsURL(url)) continue;
                    Connection connection = driver.connect(url, properties);
                    if (connection != null) return connection;
                } catch (SQLException ignored) {
                    // Try the next driver; report the original failure if none work.
                }
            }
            throw first;
        }
    }

    // ========== Connection ==========

    /** One open database connection. Not safe to share between threads. */
    public static final class Conn {
        private final Connection connection;
        private final String url;

        Conn(Connection connection, String url) {
            this.connection = connection;
            this.url = url;
        }

        /**
         * Runs a query and returns every row as a Map from column label to value, in the
         * order the columns were selected.
         *
         * @param params bound in order to the `?` placeholders.
         */
        public List query(String sql, Object... params) {
            try (PreparedStatement statement = prepare(sql, params);
                 ResultSet results = statement.executeQuery()) {
                return readAll(results, sql);
            } catch (SQLException e) {
                throw failure("query", sql, e);
            }
        }

        /** The first row, or null when there are none. */
        public Map queryOne(String sql, Object... params) {
            List rows = query(sql, params);
            return rows.isEmpty() ? null : (Map) rows.get(0);
        }

        /** The first column of the first row, or null. What a COUNT or a MAX wants. */
        public Object queryValue(String sql, Object... params) {
            try (PreparedStatement statement = prepare(sql, params);
                 ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getObject(1) : null;
            } catch (SQLException e) {
                throw failure("query", sql, e);
            }
        }

        /** Runs an INSERT, UPDATE, DELETE or DDL statement; returns rows affected. */
        public int update(String sql, Object... params) {
            try (PreparedStatement statement = prepare(sql, params)) {
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw failure("update", sql, e);
            }
        }

        /**
         * Runs the body in a transaction: commits when it returns, rolls back when it
         * throws, and rethrows either way.
         *
         * There is no `begin`/`commit` pair to forget. A body that throws between a manual
         * pair leaves the connection holding an open transaction, and the next unrelated
         * statement joins it.
         */
        public void transaction(Work work) {
            if (work == null) throw new IllegalArgumentException("Db: work must not be null");
            boolean previous;
            try {
                previous = connection.getAutoCommit();
                connection.setAutoCommit(false);
            } catch (SQLException e) {
                throw new RuntimeException("Db: could not begin a transaction on " + url
                    + " (" + e.getMessage() + ")", e);
            }
            try {
                work.run(this);
                connection.commit();
            } catch (Throwable failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    // The rollback failing must not replace the reason we are rolling back.
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                if (failure instanceof Error error) throw error;
                throw new RuntimeException("Db: transaction failed on " + url
                    + " (" + failure.getMessage() + ")", failure);
            } finally {
                try {
                    connection.setAutoCommit(previous);
                } catch (SQLException ignored) {
                    // The connection is on its way out; the original outcome stands.
                }
            }
        }

        public boolean isClosed() {
            try {
                return connection.isClosed();
            } catch (SQLException e) {
                return true;
            }
        }

        /** Idempotent. */
        public void close() {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Closing an already-broken connection is not a failure worth handling.
            }
        }

        private PreparedStatement prepare(String sql, Object[] params) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(sql);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
            }
            return statement;
        }

        private RuntimeException failure(String what, String sql, SQLException e) {
            return new RuntimeException("Db: " + what + " failed (" + e.getMessage() + "): " + sql, e);
        }

        private static List readAll(ResultSet results, String sql) throws SQLException {
            ResultSetMetaData meta = results.getMetaData();
            int columns = meta.getColumnCount();
            List<String> labels = new ArrayList<>(columns);
            for (int i = 1; i <= columns; i++) {
                // The label, not the name: it is what honours `SELECT x AS y`.
                String label = meta.getColumnLabel(i);
                if (labels.contains(label)) {
                    // Silently keeping one of them would lose a column the query asked
                    // for, and the caller would never find out. Say so instead.
                    throw new IllegalStateException(
                        "Db: two columns are both labelled \"" + label
                            + "\", so a row cannot be a Map — alias one with AS: " + sql);
                }
                labels.add(label);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (results.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(labels.get(i - 1), results.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }
}
