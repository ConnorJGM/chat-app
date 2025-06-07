// ClientHandler.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.database.Database;
import com.connorgillmead.chat.server.database.MessageDBHandler;
import java.io.*;
import java.net.Socket;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Handles communication with a single client.
 * This class is responsible for reading messages from the client,
 * processing them, and sending responses back to the client.
 * It implements the Runnable interface to allow it to be run in a separate
 * thread.
 */
public class ClientHandler implements Runnable {
    // Instance variables for the ClientHandler class.
    private final Socket socket;
    private final ChatServerHub hub;
    private String username;
    private PrintWriter out;
    private boolean authenticated;

    /**
     * Constructor for ClientHandler.
     * Initialises the socket and hub for the client.
     *
     * @param socket The socket representing the connection to the client.
     * @param hub    The ChatServerHub instance managing all clients.
     */
    ClientHandler(Socket socket, ChatServerHub hub) {
        this.socket = socket;
        this.hub = hub;
    }

    /**
     * Sets the username of the client.
     * This method is used to update the username of the client for web.
     *
     * @param username The new username of the client.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the username of the client.
     * This method is used to identify the client in the chat.
     *
     * @return The username of the client.
     */
    String getUsername() {
        return username;
    }

    /**
     * Returns whether the client is authenticated.
     * This method is used to check if the client has successfully logged in.
     *
     * @return true if the client is authenticated, false otherwise.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Sets the authentication status of the client.
     * This method is used to update the authentication status of the client.
     *
     * @param authenticated The new authentication status of the client.
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * Returns the socket representing the connection to the client.
     * This method is used to get the socket for sending messages to the client.
     *
     * @return The socket representing the connection to the client.
     */
    ChatServerHub getHub() {
        return hub;
    }

    /**
     * Returns the socket representing the connection to the client.
     * This method is used to get the socket for sending messages to the client.
     *
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            // Get the output stream for sending messages to the client.
            // The PrintWriter is used to send text data to the client.
            // The 'true' argument enables auto-flushing (output stream flushed).
            out = new PrintWriter(socket.getOutputStream(), true);

            while (!authenticated) {
                // Read the next line from the client.
                String line = in.readLine();
                if (line == null) {
                    System.out.println("Client disconnected before authentication: " + socket.getRemoteSocketAddress());
                    return;
                }
                // Parse the message from JSON format.
                ChatMessage authorisedMessage = ChatMessage.fromJson(line);
                String requestUsername = authorisedMessage.getUser();
                String requestPassword = authorisedMessage.getPassword();

                if (requestUsername == null || requestUsername.isBlank()
                        || ("register".equals(authorisedMessage.getType())
                                || "login".equals(authorisedMessage.getType()))
                                && (requestPassword == null || requestPassword.isBlank())) {
                    send(ChatMessage.authorisedResponse(authorisedMessage.getType(), requestUsername,
                            false, "Username or password cannot be empty"));
                    continue;
                }

                if ("register".equals(authorisedMessage.getType())) {
                    if (Database.userExists(requestUsername)) {
                        send(ChatMessage.authorisedResponse("register_response", requestUsername, false,
                                "Username already exists. Please try logging in or use a different username."));
                    } else {
                        if (Database.addUser(requestUsername, requestPassword)) {
                            processSuccessfulRegistrationAndReserveName(requestUsername);
                        } else {
                            send(ChatMessage.authorisedResponse("register_response", requestUsername, false,
                                    "Failed to register user. "
                                            + "The username might be taken or a server error occurred."));
                        }
                    }
                    continue;

                } else if ("login".equals(authorisedMessage.getType())) {
                    String requiredToken = hub.getToken();
                    if (requiredToken != null && !requiredToken.isBlank()
                            && !requiredToken.equals(authorisedMessage.getToken())) {
                        send(ChatMessage.authorisedResponse(
                                "login_response",
                                requestUsername, false,
                                "Invalid token"));
                        continue;
                    }
                    loginAttempt(requestUsername, requestPassword);
                    continue;
                } else {
                    send(ChatMessage.authorisedResponse(authorisedMessage.getType(), requestUsername, false,
                            "Registration failed."));
                }
            }

            // If the username is not set, read the first message from the client.
            // This is typically the "hello" message sent by the client to identify itself.
            if (!this.authenticated || this.username == null) {
                String firstLine = in.readLine();
                if (firstLine == null) {
                    socket.close();
                    return;
                }
                ChatMessage firstMsg = ChatMessage.fromJson(firstLine);
                if (!"hello".equals(firstMsg.getType())) {
                    out.println(ChatMessage.error("Must send 'hello' first"));
                    socket.close();
                    return;
                }
                String serverToken = hub.getToken();
                if (serverToken != null && !serverToken.isBlank()
                        && !serverToken.equals(firstMsg.getToken())) {
                    out.println(ChatMessage.error("Invalid token"));
                    socket.close();
                    return;
                }
                this.username = firstMsg.getUser();
                hub.broadcast(firstMsg);
            }

            System.out.println(this.username + " connected from " + socket);
            hub.addClient(this);
            hub.getHistory().forEach(this::send);
            send(ChatMessage.userList(hub.getUsernames()));

            handleClientMessages(in);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (username != null && authenticated) {
                // Clean up resources when the client disconnects
                // and notify other clients about the disconnection.
                hub.broadcast(ChatMessage.bye(username));
                hub.releaseName(username);
                hub.removeClient(this);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends a message to the client.
     * This method is called to send a message to the client.
     *
     * @param msg The message to send to the client.
     */
    void send(ChatMessage msg) {
        if (out != null) {
            out.println(msg.toJson());
        }
    }

    /**
     * Closes the connection to the client.
     * This method is called to close the socket and release any associated
     * resources.
     */
    public void disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Attempts to log in the user with the provided username and password.
     * This method checks if the user exists in the database, retrieves the stored
     * password hash, and verifies the password using BCrypt.
     *
     * @param requestUsername The username provided by the client.
     * @param requestPassword The password provided by the client.
     */
    private void loginAttempt(String requestUsername, String requestPassword) {
        // Check if the user exists in the database.
        if (!Database.userExists(requestUsername)) {
            send(ChatMessage.authorisedResponse("login_response", requestUsername, false,
                    "User does not exist"));
            return;
        }

        // Get the stored password hash for the user.
        String storedHash = Database.getPasswordHash(requestUsername);
        if (storedHash == null) {
            send(ChatMessage.authorisedResponse("login_response", requestUsername, false,
                    "Failed to retrieve password hash"));
            return;
        }

        // Verify the password using BCrypt.
        if (BCrypt.checkpw(requestPassword, storedHash)) {
            if (!hub.reserveName(requestUsername)) {
                send(ChatMessage.authorisedResponse("login_response", requestUsername, false,
                        "User is already logged in."));
                return;
            }
            this.username = requestUsername;
            this.authenticated = true;
            send(ChatMessage.authorisedResponse("login_response", requestUsername, true,
                    "Login successful. Welcome " + requestUsername + "!"));
        } else {
            send(ChatMessage.authorisedResponse("login_response", requestUsername, false,
                    "Invalid username or password."));
        }
    }

    /**
     * Processes a successful user registration by attempting to reserve the
     * username in the hub
     * and authenticating the client.
     *
     * @param requestUsername The username that was successfully registered in the
     *                        database.
     */
    private void processSuccessfulRegistrationAndReserveName(String requestUsername) {
        System.err.println("SERVER: User " + requestUsername + " added to DB. Attempting to reserve name.");
        if (!hub.reserveName(requestUsername)) {
            System.err.println(
                    "SERVER: hub.reserveName for " + requestUsername + " failed after DB add.");
            send(ChatMessage.authorisedResponse("register_response", requestUsername, false,
                    "Registration successful, but failed to reserve "
                            + "username for this session. Please try logging in."));
        } else {
            System.err.println("SERVER: Registration and name reservation for " + requestUsername
                    + " successful.");
            this.username = requestUsername;
            this.authenticated = true;
            send(ChatMessage.authorisedResponse("register_response", requestUsername, true,
                    "Registration successful. Welcome " + requestUsername + "!"));
        }
    }

    /**
     * Handles messages from the client.
     * This method reads messages from the client, processes them, and sends
     * responses back to the client.
     * It continues to read messages until the client disconnects or an I/O error
     * occurs.
     *
     * @param input The BufferedReader for reading messages from the client.
     * @throws IOException If an I/O error occurs while reading from the client.
     */
    private void handleClientMessages(BufferedReader input) throws IOException {
        // Read messages from the client and broadcast them to all clients.
        // This loop continues until the client disconnects or an I/O error occurs.
        String line;
        while ((line = input.readLine()) != null) {
            ChatMessage message = ChatMessage.fromJson(line);

            switch (message.getType()) {
                case "register" -> {
                    String requiredToken = hub.getToken();
                    if (requiredToken != null && !requiredToken.isBlank()
                            && !requiredToken.equals(message.getToken())) {
                        send(ChatMessage.authorisedResponse(
                                "register_response",
                                message.getUser(), false,
                                "Invalid token"));
                        continue;
                    }
                    if (Database.userExists(message.getUser())) {
                        send(ChatMessage.authorisedResponse("register_response", message.getUser(), false,
                                "Username already exists. Please try logging in or use a different username."));
                    } else {
                        if (Database.addUser(message.getUser(), message.getPassword())) {
                            processSuccessfulRegistrationAndReserveName(message.getUser());
                        } else {
                            send(ChatMessage.authorisedResponse("register_response", message.getUser(), false,
                                    "Failed to register user. "
                                            + "The username might be taken or a server error occurred."));
                        }
                    }
                    continue;
                }
                case "login" -> {
                    String requiredToken = hub.getToken();
                    if (requiredToken != null && !requiredToken.isBlank()
                            && !requiredToken.equals(message.getToken())) {
                        send(ChatMessage.authorisedResponse(
                                "login_response",
                                message.getUser(), false,
                                "Invalid token"));
                        continue;
                    }
                    loginAttempt(message.getUser(), message.getPassword());
                    continue;
                }
                case "hello" -> {
                    String requiredToken = hub.getToken();
                    if (requiredToken != null && !requiredToken.isBlank()
                            && !requiredToken.equals(message.getToken())) {
                        send(ChatMessage.error("Invalid token"));
                        socket.close();
                        return;
                    }
                    hub.broadcast(ChatMessage.hello(message.getUser(), message.getToken()));
                    continue;
                }
                case "text" -> {
                    if (CommandHelper.command(message, hub, this)) {
                        continue;
                    }
                    MessageDBHandler.addMessage(this.username, message.getBody());
                    ChatMessage text = ChatMessage.of(this.username, message.getBody());
                    hub.broadcast(text);
                    continue;
                }
                case "bye" -> {
                    hub.broadcast(ChatMessage.bye(this.username));
                    break;
                }
                default -> {
                    send(ChatMessage.error("Unknown message type: " + message.getType()));
                    continue;
                }
            }
        }
    }
}
