// Database.java

package com.connorgillmead.chat.server.database;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.mindrot.jbcrypt.BCrypt;

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

    /**
     * Adds a new user to the database.
     * This method checks if the user already exists, and if not, it hashes the
     * password and inserts the user into the database.
     *
     * @param username The username of the user to be added.
     * @param password The password of the user to be added.
     * @return True if the user was added successfully, false if the user already
     *         exists.
     */
    public static boolean addUser(String username, String password) {
        if (userExists(username)) {
            System.err.println(username + " already exists.");
            return false;
        }
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users (username, pass_hash) VALUES (?, ?)";
        try (Connection connection = connection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            preparedStatement.setString(2, hashedPassword);
            preparedStatement.executeUpdate();
            System.out.println("User " + username + " added successfully.");
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding user: " + username + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets the password hash for a given user.
     * This method retrieves the password hash for a user from the database.
     * It is used for authentication purposes.
     *
     * @param username The username of the user whose password hash is to be
     *                 retrieved.
     * @return The password hash of the user, or null if the user does not exist.
     */
    public static String getPasswordHash(String username) {
        String sql = "SELECT pass_hash FROM users WHERE username = ?";
        try (Connection connection = connection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("pass_hash");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving password hash for user: " + username + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Checks if a user exists in the database.
     * This method checks if a user with the given username already exists in the
     * database.
     *
     * @param username The username to check for existence.
     * @return True if the user exists, false otherwise.
     */
    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        try (Connection connection = connection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if user exists: " + username + ": " + e.getMessage());
        }
        return false;
    }
}
