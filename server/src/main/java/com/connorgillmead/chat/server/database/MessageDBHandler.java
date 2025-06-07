package com.connorgillmead.chat.server.database;

import java.sql.SQLException;

/**
 * MessageDBHandler is a utility class that provides methods for handling
 * messages in the chat database.
 * It allows adding messages to the database and retrieving user IDs based on
 * usernames.
 */
public final class MessageDBHandler {

    /**
     * Private constructor to prevent instantiation.
     */
    private MessageDBHandler() {
    }

    /**
     * Adds a message to the database.
     *
     * @param username The username of the user sending the message.
     * @param body     The content of the message.
     * @return true if the message was added successfully, false otherwise.
     */
    public static boolean addMessage(String username, String body) {
        Integer userId = getUserId(username);
        if (userId == null) {
            System.err.println("User not found: " + username);
            return false;
        }
        String sql = "INSERT INTO messages (user_id, content) VALUES (?, ?)";
        try (var connection = Database.connection();
                var preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setInt(1, userId);
            preparedStatement.setString(2, body);

            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error inserting message for " + username + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the user ID for a given username.
     *
     * @param username The username of the user.
     * @return The user ID if found, null otherwise.
     */
    private static Integer getUserId(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (var connection = Database.connection();
                var preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            try (var resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user ID for " + username + ": " + e.getMessage());
        }
        return null;
    }
}
