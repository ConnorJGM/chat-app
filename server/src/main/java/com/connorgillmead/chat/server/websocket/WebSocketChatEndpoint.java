// WebSocketChatEndpoint.java

package com.connorgillmead.chat.server.websocket;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.database.Database;
import com.connorgillmead.chat.server.database.MessageDBHandler;
import com.connorgillmead.chat.server.tcp.ChatServerHub;
import com.connorgillmead.chat.server.utilities.CommandHelper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.mindrot.jbcrypt.BCrypt;

/**
 * WebSocket endpoint for the chat server.
 * This class handles WebSocket connections and messages.
 * It uses the Jakarta WebSocket API to manage WebSocket sessions.
*/
@ServerEndpoint("/wschat")
public class WebSocketChatEndpoint {
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
    private static final Map<Session, WebHandler> HANDLER_MAP = new ConcurrentHashMap<>();
    private static ChatServerHub hub;

    /**
     * Returns the set of WebSocket sessions.
     * This method is used to get the current WebSocket sessions.
     * @return The set of WebSocket sessions.
    */
    public static Set<Session> getSessions() {
        return SESSIONS;
    }

    /**
     * Returns the map of WebSocket sessions to WebHandler instances.
     * This method is used to get the current WebSocket handlers.
     * @return The map of WebSocket sessions to WebHandler instances.
    */
    public static Map<Session, WebHandler> getHandlerMap() {
        return HANDLER_MAP;
    }

    /**
     * Handles the opening of a WebSocket connection.
     * This method is called when a new WebSocket connection is established.
     * @param session The WebSocket session that was opened.
    */
    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
        WebHandler handler = new WebHandler(session, hub, null);
        HANDLER_MAP.put(session, handler);
    }

    /**
     * Handles incoming messages from the WebSocket client.
     * This method is called when a message is received from the client.
     * @param json The JSON string representing the ChatMessage.
     * @param session The WebSocket session that sent the message.
    */
    @OnMessage
    public void onMessage(String json, Session session) throws IOException {
        ChatMessage message = ChatMessage.fromJson(json);
        WebHandler handler = HANDLER_MAP.get(session);

        // Check if the message is a "hello" message.
        // If the handler is not authenticated, check if the message is a "hello" message.
        if (handler != null && !handler.isAuthenticated()) {
            switch  (message.getType().trim()) {
                case "login":
                    handleLogin(message, handler, session);
                    return;
                case "register":
                    handleRegister(message, handler, session);
                    return;
                case "hello":
                    // Required token validation
                    // If the server requires a token, check if the provided token matches the required token.
                    String requiredToken = hub.getToken();
                    if (requiredToken != null && !requiredToken.isBlank()
                        && !requiredToken.equals(message.getToken())) {
                        System.out.println("WebSocket: Invalid or missing token for user: " + message.getUser());
                        session.close();
                        return;
                    }
                    // Check if the username is already in use.
                    // If the username is already in use, close the session.
                    if (!hub.reserveName(message.getUser())) {
                        System.out.println("WebSocket: Username already in use: " + message.getUser());
                        session.close();
                        return;
                    }
                    // Set the username and authenticated status for the handler.
                    handler.setUsername(message.getUser());
                    handler.setAuthenticated(true);
                    hub.getHistory().forEach(handler::send);
                    hub.addClient(handler);
                    System.out.println(handler.getUsername() + " connected on WebSocket: " + session.getId());
                    // Broadcast the "hello" message to all connected clients.
                    hub.broadcast(ChatMessage.hello(message.getUser(), null));
                    return;
                default:
                    handler.send(ChatMessage.error("Invalid message type: " + message.getType()));
                    return;
            }
        }

        // If the handler is authenticated, process the message.
        // If the message is a command, handle it accordingly.
        if (handler != null && handler.isAuthenticated()) {
            if ("hello".equals(message.getType())) {
                hub.broadcast(ChatMessage.hello(handler.getUsername(), null));
                return;
            }
            if (CommandHelper.command(message, hub, handler)) {
                return;
            }
            if ("text".equals(message.getType())) {
                MessageDBHandler.addMessage(handler.getUsername(), message.getBody());
                ChatMessage stamped = ChatMessage.of(handler.getUsername(), message.getBody());
                hub.broadcast(stamped);
            } else {
                hub.broadcast(message);
            }
        }
    }

    /**
     * Handles user login requests.
     * This method checks if the user exists and if the password is correct.
     * If the login is successful, it sets the username and authenticated status for the handler.
     * @param message The ChatMessage containing the login request.
     * @param handler The WebHandler instance for the session.
     * @param session The WebSocket session that sent the message.
     * @throws IOException If an I/O error occurs while sending a response.
    */
    private void handleLogin(ChatMessage message, WebHandler handler, Session session) throws IOException {
        String username = message.getUser();
        String password = message.getPassword();
        String clientToken = message.getToken();

        String requiredToken = hub.getToken();
        if (requiredToken != null && !requiredToken.isBlank()) {
            if (clientToken == null || !clientToken.equals(requiredToken) || !requiredToken.equals(clientToken)) {
                handler.send(createLoginResponse(false, "Invalid or missing token.", null, null));
                return;
            }
        }

        if (!Database.userExists(username)) {
            handler.send(createLoginResponse(false, "User does not exist.", null, null));
            return;
        }

        String storedHash = Database.getPasswordHash(username);
        if (storedHash == null) {
            handler.send(createLoginResponse(false, "Authentication Error.", null, null));
            return;
        }
        if (!BCrypt.checkpw(password, storedHash)) {
            handler.send(createLoginResponse(false, "Invalid password.", null, null));
            return;
        }

        if (!hub.reserveName(username)) {
            handler.send(createLoginResponse(false, "User is already logged in.", null, null));
            return;
        }

        handler.setUsername(username);
        handler.setAuthenticated(true);
        hub.getHistory().forEach(handler::send);
        hub.addClient(handler);
        System.out.println(username + " connected from WebSocket: " + session.getId());
        handler.send(createLoginResponse(true, "Login successful", username, requiredToken));
    }

    /**
     * Handles user registration requests.
     * This method checks if the username already exists and if not, adds the user to the database.
     * If the registration is successful, it sends a response to the client.
     * @param message The ChatMessage containing the registration request.
     * @param handler The WebHandler instance for the session.
     * @param session The WebSocket session that sent the message.
     * @throws IOException If an I/O error occurs while sending a response.
    */
    private void handleRegister(ChatMessage message, WebHandler handler, Session session) throws IOException {
        String username = message.getUser();
        String password = message.getPassword();

        if (Database.userExists(username)) {
            handler.send(createRegisterResponse(false, "Username already exists."));
            return;
        }

        if (Database.addUser(username, password)) {
            handler.send(createRegisterResponse(true, "Registration successful for " + username));
            return;
        } else {
            handler.send(createRegisterResponse(false, "Registration failed. Please try again."));
        }
    }

    /**
     * Creates a login response message.
     * This method is used to create a response message for login requests.
     * @param success Indicates whether the login was successful or not.
     * @param message The message to be sent in the response.
     * @param username The username of the user who logged in.
     * @return A ChatMessage object representing the login response.
    */
    private ChatMessage createLoginResponse(boolean success, String message, String username, String token) {
        ChatMessage response = ChatMessage.authorisedResponse("login_response", username, success, message);
        if (success && token != null) {
            response.setToken(token);
        }
        return response;
    }

    /**
     * Creates a registration response message.
     * This method is used to create a response message for registration requests.
     * @param success Indicates whether the registration was successful or not.
     * @param message The message to be sent in the response.
     * @return A ChatMessage object representing the registration response.
    */
    private ChatMessage createRegisterResponse(boolean success, String message) {
        return ChatMessage.authorisedResponse("register_response", null, success, message);
    }

    /**
     * Handles the closing of a WebSocket connection.
     * This method is called when the WebSocket connection is closed.
     * @param session The WebSocket session that was closed.
    */
    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
        WebHandler handler = HANDLER_MAP.remove(session);
        if (handler != null) {
            hub.broadcast(ChatMessage.bye(handler.getUsername()));
            hub.releaseName(handler.getUsername());
            hub.removeClient(handler);
        }
    }

    /**
     * Handles errors that occur during WebSocket communication.
     * This method is called when an error occurs in the WebSocket connection.
     * @param throwable The Throwable object representing the error.
    */
    @OnError
    public void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    /**
     * Sends a message to all connected WebSocket clients.
     * @param message The ChatMessage to send.
    */
    public static void sendToAll(ChatMessage message) {
        String json = message.toJson();
        for (Session session : SESSIONS) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException error) {
                    error.printStackTrace();
                }
            }
        }
    }

    /**
     * Attaches the ChatServerHub instance to the WebSocket endpoint.
     * This method is called to set the ChatServerHub instance for the WebSocket endpoint.
     * @param h The ChatServerHub instance to attach.
    */
    public static void attachHub(ChatServerHub h) {
        hub = h;
    }
}
