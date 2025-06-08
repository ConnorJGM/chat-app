// ChatMessage.java

package com.connorgillmead.chat.common;

// Gson library for JSON serialisation/deserialisation.
// This library is used to convert Java objects to JSON format and vice versa.
// It is a popular library for working with JSON in Java applications.
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;

/**
 * ChatMessage is a class that represents a message in the chat application.
 * It contains various fields to store information about the message, such as
 * the type of message, the user who sent it, the body of the message, and
 * additional metadata like time, token, user list, password, success status,
 * and message content.
 * <p>
 * This class provides methods to create different types of chat messages,
 * convert them to JSON format, and deserialize JSON strings back into
 * ChatMessage objects.
 */
public final class ChatMessage {
    /**
     * This message type is used to notify users that the server is shutting down.
     */
    public static final String SERVER_SHUTDOWN = "server_shutdown";
    private static final Gson GSON = new Gson();

    // Gson instance for logging purposes.
    // This instance excludes the "success" field from the JSON output.
    private static final Gson GSON_FOR_LOG = new GsonBuilder()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes fieldAttributes) {
                    return fieldAttributes.getName().equals("success")
                        || fieldAttributes.getName().equals("password")
                        || fieldAttributes.getName().equals("token");
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            }).create();

    private String type;
    private String user;
    private String body;
    private long   time;
    private String token;
    private List<String> users;
    private String password;
    private boolean success;
    private String message;

    /**
     * Default constructor for ChatMessage.
     * This constructor is used for deserialisation from JSON.
     * It initializes the fields to their default values.
     */
    public ChatMessage() { }

    /**
     * Default constructor for ChatMessage.
     * This constructor is used for deserialisation from JSON.
     * It initializes the fields to their default values.
     *
     * @return A new instance of ChatMessage with default values.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the user who sent the chat message.
     * The user is typically the username or identifier of the person who sent the message.
     *
     * @return The user who sent the chat message.
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the body of the chat message.
     * The body contains the actual content of the message, such as text or media.
     *
     * @return The body of the chat message.
     */
    public String getBody() {
        return body;
    }

    /**
     * Returns the timestamp of when the chat message was sent.
     * The time is represented as milliseconds since the epoch (January 1, 1970).
     *
     * @return The timestamp of the chat message in milliseconds.
     */
    public long getTime() {
        return time;
    }

    /**
     * Returns the token associated with the chat message.
     * The token is used for authentication or session management.
     * It can be null if not set.
     *
     * @return The token associated with the chat message, or null if not set.
     */
    public String getToken() {
        return token;
    }

    /**
     * Returns the list of users connected to the chat.
     * This list is typically used to display the roster of users in the chat application.
     *
     * @return The list of users connected to the chat.
     */
    public List<String> getUserList() {
        return users;
    }

    /**
     * Returns the password associated with the chat message.
     * This field is used for authentication purposes, such as logging in or registering a user.
     * It can be null if not set.
     *
     * @return The password associated with the chat message, or null if not set.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns whether the chat message indicates a successful operation.
     * This field is typically used in responses to login or registration requests.
     *
     * @return true if the operation was successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the message associated with the chat message.
     * This field is used to provide additional information or context about the message,
     * such as error messages or notifications.
     *
     * @return The message associated with the chat message, or null if not set.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the token for the chat message.
     * This method is used to set the token for authentication or session management.
     * @param token The token to be set for the chat message.
     *             This could be a session token or a unique identifier for the user.
     */
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
     * @return A new ChatMessage object with the specified user, body, and time.
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

    /**
     * Creates a new ChatMessage object for message history.
     * This message type is used to send a message from the chat history.
     *
     * @param user The user who sent the message.
     * @param body The body of the message.
     * @param time The timestamp of when the message was sent.
     * @return A new ChatMessage object with the specified user, body, and time.
     */
    public static ChatMessage messageHistory(String user, String body, long time) {
        ChatMessage m = new ChatMessage();
        m.type = "text";
        m.user = user;
        m.body = body;
        m.time = time;
        return m;
    }

    /**
     * Creates a new ChatMessage object for server shutdown.
     * This message type is used to notify users that the server is shutting down.
     *
     * @param reason The reason for the server shutdown.
     * @return A new ChatMessage object with the specified reason for shutdown.
     */
    public static ChatMessage serverShutdown(String reason) {
        ChatMessage m = new ChatMessage();
        m.type = SERVER_SHUTDOWN;
        m.user = "Server";
        m.body = reason;
        m.time = System.currentTimeMillis();
        return m;
    }

    /**
     * Converts the ChatMessage object to a JSON string.
     * This method uses the Gson library to serialise the ChatMessage object into JSON format.
     * @return A JSON string representation of the ChatMessage object.
     *         This string can be used for sending messages over the network or for logging purposes.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Converts the ChatMessage object to a JSON string for logging purposes.
     * This method uses a custom Gson instance that excludes certain fields from the JSON output.
     * @return A JSON string representation of the ChatMessage object, suitable for logging.
     *         This string does not include sensitive information such as passwords or tokens.
     */
    public String toLogJson() {
        return GSON_FOR_LOG.toJson(this);
    }

    /**
     * Converts a JSON string to a ChatMessage object.
     * This method uses the Gson library to deserialise the JSON string into a ChatMessage object.
     *
     * @param j The JSON string to be deserialised.
     * @return A ChatMessage object created from the JSON string.
     *         If the JSON string is invalid or does not match the ChatMessage structure,
     *         an exception will be thrown.
     */
    public static ChatMessage fromJson(String j) {
        return GSON.fromJson(j, ChatMessage.class);
    }
}
