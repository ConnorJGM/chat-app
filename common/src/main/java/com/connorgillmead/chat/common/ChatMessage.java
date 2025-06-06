// ChatMessage.java

package com.connorgillmead.chat.common;

// Gson library for JSON serialisation/deserialisation.
// This library is used to convert Java objects to JSON format and vice versa.
// It is a popular library for working with JSON in Java applications.
import com.google.gson.Gson;
import java.util.List;

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
    private String token;
    private List<String> users;
    private String password;
    private boolean success;
    private String message;

    // Getters for the fields.
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

    public String getToken() {
        return token;
    }

    public List<String> getUserList() {
        return (List<String>) users;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public void setToken(String token) {
        this.token = token;
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
        m.type = "text";
        m.user = user;
        m.body = body;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object with the specified user and token.
     * This message type is used for authentication or connection establishment.
     * @param user The user who is connecting to the chat.
     * @param token The token for authentication or connection establishment.
     *             This could be a session token or a unique identifier for the user.
     * @return A new ChatMessage object with the specified user and token.
     *         The type is set to "hello" to indicate a connection request.
     */
    public static ChatMessage hello(String user, String token) {
        ChatMessage m = new ChatMessage();
        m.type = "hello";
        m.user = user;
        m.body = "joined the chat.";
        m.time = System.currentTimeMillis();
        m.token = token;
        return m;
    }

    /**
     * Creates a new ChatMessage object with the specified error.
     * This message type is used for error messages.
     *
     * @param text The string of the error.
     * @return A new ChatMessage object with the specified error.
     *         The type is set to "error" to indicate an error.
     */
    public static ChatMessage error(String text) {
        ChatMessage m = new ChatMessage();
        m.type = "error";
        m.body = text;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object with the specified type, user, body, and time.
     * The "bye" message type is used to indicate that a user has left the chat.
     * This constructor is used for deserialization from JSON.
     *
     * @param user The user who sent the message.
     */
    public static ChatMessage bye(String user) {
        ChatMessage m = new ChatMessage();
        m.type = "bye";
        m.user = user;
        m.body = "left the chat.";
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object with the specified notice.
     * This message type is used to notify users of a kick event.
     * The "kick" message type is used to indicate that a user has been kicked from the chat.
     *
     * @param notice The notice message to be sent to the user.
     * @return A new ChatMessage object with the specified notice.
     */
    public static ChatMessage kick(String notice) {
        ChatMessage m = new ChatMessage();
        m.type = "kick";
        m.user = "Server";
        m.body = notice;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Checks if the message type is "kick".
     * This method is used to determine if the message is a kick notification.
     *
     * @return true if the message type is "kick", false otherwise.
     */
    public boolean isKick() {
        return "kick".equals(type);
    }

    /**
     * Creates a new ChatMessage object with the specified list of users.
     * This message type is used to send the list of connected users to the client.
     *
     * @param users The list of connected users.
     * @return A new ChatMessage object with the specified list of users.
     *         The type is set to "roster" to indicate a user list message.
     */
    public static ChatMessage userList(List<String> users) {
        ChatMessage m = new ChatMessage();
        m.type = "roster";
        m.users = List.copyOf(users);
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object for user login.
     * This message type is used to authenticate a user with the server.
     *
     * @param username The username of the user trying to log in.
     * @param password The password of the user trying to log in.
     * @return A new ChatMessage object with the specified username and password.
     */
    public static ChatMessage login(String username, String password) {
        ChatMessage m = new ChatMessage();
        m.type = "login";
        m.user = username;
        m.password = password;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object for user registration.
     * This message type is used to register a new user with the server.
     *
     * @param username The username of the user trying to register.
     * @param password The password of the user trying to register.
     * @return A new ChatMessage object with the specified username and password.
     */
    public static ChatMessage register(String username, String password) {
        ChatMessage m = new ChatMessage();
        m.type = "register";
        m.user = username;
        m.password = password;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Creates a new ChatMessage object for an authorised response.
     * This message type is used to send a response to an authorised request.
     *
     * @param type The type of the response (e.g., "login_response", "register_response").
     * @param user The user who is receiving the response.
     * @param success Indicates whether the request was successful or not.
     * @param responseMessage The message to be sent in the response.
     * @return A new ChatMessage object with the specified type, user, success status, and response message.
     */
    public static ChatMessage authorisedResponse(String type, String user, boolean success, String responseMessage) {
        ChatMessage m = new ChatMessage();
        m.type = type;
        m.user = user;
        m.success = success;
        m.message = responseMessage;
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
