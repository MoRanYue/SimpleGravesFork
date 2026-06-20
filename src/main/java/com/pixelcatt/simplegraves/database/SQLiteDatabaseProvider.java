package com.pixelcatt.simplegraves.database;

import com.pixelcatt.simplegraves.SimpleGraves;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link DatabaseProvider} for local SQLite databases.
 * <p>
 * Uses HikariCP connection pool (max 1 connection) for consistency with
 * {@link PostgreSQLDatabaseProvider}. The database file is stored at
 * {@code plugins/SimpleGraves/graves.db}.
 */
public class SQLiteDatabaseProvider implements DatabaseProvider {

    private HikariDataSource dataSource;

    @Override
    public void initialize(SimpleGraves plugin) throws Exception {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File dbFile = new File(plugin.getDataFolder(), "graves.db");

        // Register the JDBC driver explicitly so HikariCP can find it
        // (Bukkit's PluginClassLoader doesn't participate in DriverManager's
        //  service-provider loading mechanism)
        Class.forName("org.sqlite.JDBC");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1);

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("SQLite database opened: " + dbFile.getAbsolutePath());
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("SQLite data source is not initialised");
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
    //  SQLite-specific SQL templates
    // ----------------------------------------------------------

    @Override
    public String getCreateGravesTableSQL() {
        return "CREATE TABLE IF NOT EXISTS graves (" +
                "uuid TEXT NOT NULL," +
                "grave_num INT NOT NULL," +
                "world TEXT NOT NULL," +
                "x DOUBLE NOT NULL," +
                "y DOUBLE NOT NULL," +
                "z DOUBLE NOT NULL," +
                "pitch DOUBLE NOT NULL," +
                "yaw DOUBLE NOT NULL," +
                "items TEXT NOT NULL," +
                "xp DOUBLE NOT NULL)";
    }

    @Override
    public String getCreateOfflinePlayersTableSQL() {
        return "CREATE TABLE IF NOT EXISTS offline_players (" +
                "uuid TEXT NOT NULL," +
                "plr_name TEXT NOT NULL UNIQUE," +
                "PRIMARY KEY(uuid))";
    }

    @Override
    public String getInsertOrUpdateOfflinePlayerSQL() {
        return "REPLACE INTO offline_players(uuid, plr_name) VALUES (?, ?)";
    }
}
