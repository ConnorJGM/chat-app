package com.connorgillmead.chat.server.database;

import com.connorgillmead.chat.common.ChatMessage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * Retrieves the last 'limit' messages from the database.
     *
     * @param limit The maximum number of messages to retrieve.
     * @return A list of ChatMessage objects containing the retrieved messages.
     */
    public static List<ChatMessage> getMessages(int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT messages.content, messages.created_at, users.username "
                     + "FROM messages "
                     + "JOIN users ON messages.user_id = users.id "
                     + "ORDER BY messages.created_at DESC "
                     + "LIMIT ?";
        try (var connection = Database.connection();
            var preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, limit);
            try (var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String content = resultSet.getString("content");
                    long timestamp = resultSet.getTimestamp("created_at").getTime();
                    String dbUsername = resultSet.getString("username");
                    messages.add(ChatMessage.messageHistory(dbUsername, content, timestamp));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving messages for " + e.getMessage());
        }
        Collections.reverse(messages);
        return messages;
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
