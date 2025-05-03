// ChatMessage.java

package com.connorgillmead.chat.common;

// Gson library for JSON serialisation/deserialisation.
// This library is used to convert Java objects to JSON format and vice versa.
// It is a popular library for working with JSON in Java applications.
import com.google.gson.Gson;

/**
 * Represents a chat message in the chat application.
 * This class contains the message type, user, body, and timestamp.
 * It provides methods to convert the message to and from JSON format.
 * The message type is used to distinguish between different types of messages (e.g., text, image).
 */
public final class ChatMessage {
    private static final Gson GSON = new Gson();

    private String type;
    private String user;
    private String body;
    private long   time;

    // Getters for the fields
    public String getType() {
        return type;
    }

    public String getUser() {
        return user;
    }

    public String getBody() {
        return body;
    }

    public long getTime() {
        return time;
    }

    /**
     * Creates a new ChatMessage object with the specified user and body.
     * The time is set to the current time in milliseconds since the epoch.
     *
     * @param user The user who sent the message.
     * @param body The body of the message.
     * @return A new ChatMessage object with the specified user and body.
     */
    public static ChatMessage of(String user, String body) {
        ChatMessage m = new ChatMessage();
        m.user = user;
        m.body = body;
        m.time = System.currentTimeMillis();
        return m;
    }

    /// Converts the ChatMessage object to a JSON string.
    /// This method uses the Gson library to serialize the object to JSON format.
    public String toJson() {
        return GSON.toJson(this);
    }

    /// Converts a JSON string to a ChatMessage object.
    /// This method uses the Gson library to deserialize the JSON string into a ChatMessage object.
    public static ChatMessage fromJson(String j) {
        return GSON.fromJson(j, ChatMessage.class);
    }
}
