package net.oneblock.economy.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {
    private final Connection connection;
    private final ExecutorService writer;
    private final Logger logger;

    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public Database(File file, Logger logger) throws SQLException {
        this.logger = logger;
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "OneblockEconomy-DB");
            t.setDaemon(true);
            return t;
        });
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        coins INTEGER NOT NULL,
                        cogs INTEGER NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        time INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        from_uuid TEXT,
                        to_uuid TEXT,
                        currency TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        info TEXT
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS tx_from ON transactions(from_uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS tx_to ON transactions(to_uuid)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS market_items (
                        material TEXT PRIMARY KEY,
                        saturation REAL NOT NULL,
                        history TEXT NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id INTEGER PRIMARY KEY,
                        buyer TEXT NOT NULL,
                        material TEXT NOT NULL,
                        price_each INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        filled INTEGER NOT NULL,
                        created INTEGER NOT NULL
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS listings (
                        id INTEGER PRIMARY KEY,
                        seller TEXT NOT NULL,
                        type TEXT NOT NULL,
                        item TEXT NOT NULL,
                        price INTEGER NOT NULL,
                        floor_price INTEGER NOT NULL,
                        created INTEGER NOT NULL,
                        expires INTEGER NOT NULL,
                        buyer TEXT,
                        per_delivery INTEGER NOT NULL DEFAULT 0,
                        deliveries_total INTEGER NOT NULL DEFAULT 0,
                        deliveries_done INTEGER NOT NULL DEFAULT 0,
                        interval_ms INTEGER NOT NULL DEFAULT 0,
                        next_delivery INTEGER NOT NULL DEFAULT 0,
                        misses INTEGER NOT NULL DEFAULT 0,
                        chest_world TEXT,
                        chest_x INTEGER NOT NULL DEFAULT 0,
                        chest_y INTEGER NOT NULL DEFAULT 0,
                        chest_z INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS collection (
                        id INTEGER PRIMARY KEY,
                        owner TEXT NOT NULL,
                        item TEXT NOT NULL,
                        source TEXT NOT NULL,
                        created INTEGER NOT NULL
                    )""");
        }
    }

    public void executeNow(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    public void execute(String sql, Object... params) {
        writer.execute(() -> {
            try (PreparedStatement ps = prepare(sql, params)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database write failed: " + sql, e);
            }
        });
    }

    public <T> CompletableFuture<List<T>> queryAsync(String sql, RowMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return queryNow(sql, mapper, params);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, writer);
    }

    public <T> List<T> queryNow(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        }
    }

    public long maxId(String table) throws SQLException {
        List<Long> rows = queryNow("SELECT COALESCE(MAX(id), 0) FROM " + table, rs -> rs.getLong(1));
        return rows.isEmpty() ? 0 : rows.getFirst();
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Database writes did not finish within 30 seconds.");
            }
            connection.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close database", e);
        }
    }
}
