// ClientHandler.java

package com.connorgillmead.chat.server;

import java.io.*;
import java.net.Socket;

/**
 * Handles communication with a single client.
 * This class is responsible for reading messages from the client,
 * processing them, and sending responses back to the client.
 * It implements the Runnable interface to allow it to be run in a separate thread.
 */
final class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServerHub hub;
    private final String username;

    private PrintWriter out;

    /**
     * Constructor for ClientHandler.
     * Initializes the socket, hub, and username for the client.
     *
     * @param socket The socket representing the connection to the client.
     * @param hub    The ChatServerHub instance managing all clients.
     * @param username The username of the client.
     */
    ClientHandler(Socket socket, ChatServerHub hub, String username) {
        this.socket = socket;
        this.hub = hub;
        this.username = username;
    }

    /**
     * Returns the username of the client.
     *
     * @return The username of the client.
     */
    String getUsername() {
        return username;
    }

    /**
     * Returns the socket representing the connection to the client.
     *
     * @return The socket of the client.
     */
    @Override public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            out = new PrintWriter(socket.getOutputStream(), true);
            hub.addClient(this);

            String line;
            while ((line = in.readLine()) != null) {
                ChatMessage msg = ChatMessage.fromJson(line);
                hub.broadcast(msg);
            }
        } catch (IOException ignored) {
        } finally {
            hub.removeClient(this);
            try {
                socket.close();
            } catch (IOException ignored) {
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
        out.println(msg.toJson());
    }
}
