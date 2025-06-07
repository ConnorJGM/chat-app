// WebHandler.java

package com.connorgillmead.chat.server.websocket;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.tcp.ChatServerHub;
import com.connorgillmead.chat.server.tcp.ClientHandler;
import jakarta.websocket.Session;
import java.io.Closeable;
import java.io.IOException;

/**
 * WebHandler is a class that handles WebSocket connections for the chat server.
 * It extends the ClientHandler class and implements the Closeable interface.
 * This class is responsible for sending messages to the client over a WebSocket connection.
 * It uses the Jakarta WebSocket API to manage the WebSocket session.
 */
public class WebHandler extends ClientHandler implements Closeable {
    private final Session session;
    private boolean authenticated;

    /**
     * Constructor for WebHandler.
     * This constructor is used to create a new WebHandler instance for a WebSocket connection.
     *
     * @param session  The WebSocket session representing the connection to the client.
     * @param hub      The ChatServerHub instance managing all clients.
     * @param username The username of the client.
     */
    public WebHandler(Session session, ChatServerHub hub, String username) {
        super(null, hub);
        this.session = session;
    }

    /**
     * Returns the WebSocket session representing the connection to the client.
     * This method is used to get the session for sending messages to the client.
     *
     * @return The WebSocket session representing the connection to the client.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * Sets the authenticated status of the client.
     * This method is used to update the authenticated status of the client for web.
     *
     * @param authenticated The new authenticated status of the client.
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     * Returns the username of the client.
     * This method is used to identify the client in the chat.
     *
     */
    public void setUsername(String username) {
        super.setUsername(username);
    }

    /**
     * Sends a message to the client over the WebSocket connection.
     * This method is used to send messages to the client in JSON format.
     * It uses the Jakarta WebSocket API to send the message.
     * @param msg The message to send to the client.
     *            The message is formatted as a JSON object.
     */
    @Override
    public void send(ChatMessage msg) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(msg.toJson());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Closes the WebSocket session.
     * This method is used to close the WebSocket connection when the client disconnects.
     */
    @Override
    public void close() {
        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
