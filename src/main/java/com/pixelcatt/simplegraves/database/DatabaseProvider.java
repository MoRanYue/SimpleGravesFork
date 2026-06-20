package com.pixelcatt.simplegraves.database;

import com.pixelcatt.simplegraves.SimpleGraves;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Abstraction layer for database backends (SQLite, PostgreSQL, etc.).
 * <p>
 * Each implementation provides:
 * <ul>
 *   <li>Connection lifecycle management</li>
 *   <li>Vendor-specific DDL statements for table creation</li>
 *   <li>Vendor-specific DML statements (e.g. REPLACE INTO vs INSERT ... ON CONFLICT)</li>
 * </ul>
 */
public interface DatabaseProvider {

    /**
     * Initialise the database connection / connection pool.
     *
     * @param plugin the SimpleGraves plugin instance (for config access and logging)
     * @throws Exception if the database cannot be reached or initialised
     */
    void initialize(SimpleGraves plugin) throws Exception;

    /**
     * Obtain a JDBC connection.
     * <p>
     * Callers <b>must not</b> close this connection if it belongs to a connection pool.
     * Use try-with-resources – the implementation guarantees that pooled connections
     * are returned to the pool on close.
     */
    Connection getConnection() throws SQLException;

    /**
     * Shut down the database provider (close connection pool, release resources).
     */
    void shutdown();

    // ----------------------------------------------------------
    //  Vendor-specific SQL templates
    // ----------------------------------------------------------

    /** DDL for the {@code graves} table. */
    String getCreateGravesTableSQL();

    /** DDL for the {@code offline_players} table. */
    String getCreateOfflinePlayersTableSQL();

    /**
     * DML for inserting or updating an offline-player row.
     * <p>
     * SQLite uses {@code REPLACE INTO}; PostgreSQL uses
     * {@code INSERT ... ON CONFLICT DO UPDATE SET ...}.
     */
    String getInsertOrUpdateOfflinePlayerSQL();
}
