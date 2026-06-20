package com.pixelcatt.simplegraves.database;

import com.pixelcatt.simplegraves.SimpleGraves;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link DatabaseProvider} for remote PostgreSQL databases.
 * <p>
 * Connection management is handled by <strong>HikariCP</strong> connection pool.
 * Configuration is read from the plugin's {@code config.yml} under the
 * {@code postgresql} section.
 */
public class PostgreSQLDatabaseProvider implements DatabaseProvider {

    private HikariDataSource dataSource;

    @Override
    public void initialize(SimpleGraves plugin) throws Exception {
        String host = plugin.getConfig().getString("postgresql.host", "localhost");
        int port = plugin.getConfig().getInt("postgresql.port", 5432);
        String database = plugin.getConfig().getString("postgresql.database", "simplegraves");
        String username = plugin.getConfig().getString("postgresql.username", "simplegraves");
        String password = plugin.getConfig().getString("postgresql.password", "");
        int maxPoolSize = plugin.getConfig().getInt("postgresql.pool-settings.maximum-pool-size", 10);
        int minIdle = plugin.getConfig().getInt("postgresql.pool-settings.minimum-idle", 2);
        int connTimeout = plugin.getConfig().getInt("postgresql.pool-settings.connection-timeout", 5000);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connTimeout);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setDriverClassName("org.postgresql.Driver");

        // Connection testing
        config.setConnectionTestQuery("SELECT 1");
        config.setConnectionInitSql("SELECT 1");

        // Performance
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("PostgreSQL connection pool initialised: " + host + ":" + port + "/" + database);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("PostgreSQL data source is not initialised");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ----------------------------------------------------------
    //  PostgreSQL-specific SQL templates
    // ----------------------------------------------------------

    @Override
    public String getCreateGravesTableSQL() {
        return "CREATE TABLE IF NOT EXISTS graves (" +
                "uuid VARCHAR(36) NOT NULL," +
                "grave_num INTEGER NOT NULL," +
                "world VARCHAR(255) NOT NULL," +
                "x DOUBLE PRECISION NOT NULL," +
                "y DOUBLE PRECISION NOT NULL," +
                "z DOUBLE PRECISION NOT NULL," +
                "pitch DOUBLE PRECISION NOT NULL," +
                "yaw DOUBLE PRECISION NOT NULL," +
                "items TEXT NOT NULL," +
                "xp DOUBLE PRECISION NOT NULL)";
    }

    @Override
    public String getCreateOfflinePlayersTableSQL() {
        return "CREATE TABLE IF NOT EXISTS offline_players (" +
                "uuid VARCHAR(36) NOT NULL," +
                "plr_name VARCHAR(16) NOT NULL UNIQUE," +
                "PRIMARY KEY(uuid))";
    }

    @Override
    public String getInsertOrUpdateOfflinePlayerSQL() {
        return "INSERT INTO offline_players(uuid, plr_name) VALUES (?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET plr_name = EXCLUDED.plr_name";
    }
}
