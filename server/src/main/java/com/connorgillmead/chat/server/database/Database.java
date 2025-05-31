package com.connorgillmead.chat.server.database;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database is a utility class that provides methods for connecting to the chat
 * database and initializing the database schema.
 * It uses SQLite as the database engine and provides a method to establish a
 * connection to the database.
 * This class is not meant to be instantiated.
 */
public final class Database {
    // Constants for the database connection.
    private static final String DB_NAME = "chat.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_NAME;

    // Private constructor to prevent instantiation.
    private Database() {
    }

    /**
     * Connects to the SQLite database.
     * This method establishes a connection to the database using the JDBC URL.
     *
     * @return A Connection object representing the database connection.
     * @throws RuntimeException If the connection fails.
     */
    public static Connection connection() {
        try {
            return DriverManager.getConnection(DB_URL);
        } catch (SQLException error) {
            throw new RuntimeException("Failed to connect to the database", error);
        }
    }

    /**
     * Initializes the chat database schema.
     * This method reads the SQL schema from a resource file and executes it to
     * create
     * the necessary tables in the database.
     *
     * @throws SQLException If an error occurs while executing the SQL statements.
     */
    public static void chatDatabase() throws SQLException {
        try (Connection connection = connection()) {
            Statement statement = connection.createStatement();
            InputStream inputStream = Database.class.getResourceAsStream("/db_chat_schema.sql");
            if (inputStream == null) {
                throw new FileNotFoundException("Cannot find resource: /db_chat_schema.sql");
            }
            String sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            String[] sqlStatements = sql.split(";");
            for (String sqlStatement : sqlStatements) {
                String trimmedStatement = sqlStatement.trim();
                if (!trimmedStatement.isEmpty()) {
                    statement.execute(trimmedStatement);
                }
            }
        } catch (SQLException error) {
            throw new RuntimeException("Failed to initialize the database schema", error);
        } catch (IOException error) {
            throw new RuntimeException("Failed to read the database schema file", error);
        }
    }
}
