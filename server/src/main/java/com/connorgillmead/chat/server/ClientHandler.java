// ClientHandler.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.*;
import java.net.Socket;

/**
 * Handles communication with a single client.
 * This class is responsible for reading messages from the client,
 * processing them, and sending responses back to the client.
 * It implements the Runnable interface to allow it to be run in a separate thread.
 */
public class ClientHandler implements Runnable {
    // Instance variables for the ClientHandler class.
    private final Socket socket;
    private final ChatServerHub hub;
    private String username;
    private PrintWriter out;

    /**
     * Constructor for ClientHandler.
     * Initialises the socket, hub, and username for the client.
     *
     * @param socket   The socket representing the connection to the client.
     * @param hub      The ChatServerHub instance managing all clients.
     * @param username The username of the client.
     */
    ClientHandler(Socket socket, ChatServerHub hub, String username) {
        this.socket = socket;
        this.hub = hub;
        this.username = username;
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

            // If the username is not set, read the first message from the client.
            // This is typically the "hello" message sent by the client to identify itself.
            if (this.username == null) {
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

            // Get history and user list from the hub.
            // The history is sent to the client to show previous messages.
            hub.getHistory().forEach(this::send);
            hub.addClient(this);
            send(ChatMessage.userList(hub.getUsernames()));

            // Read messages from the client and broadcast them to all clients.
            // This loop continues until the client disconnects or an I/O error occurs.
            String line;
            while ((line = in.readLine()) != null) {
                ChatMessage msg = ChatMessage.fromJson(line);
                // Check commands via help command.
                // The command format is "help".
                if (CommandHelper.command(msg, hub, this)) {
                    continue;
                }
                // If the message is not a command, broadcast it to all clients.
                hub.broadcast(msg);
            }
        } catch (IOException ignored) {
        } finally {
            // Clean up resources when the client disconnects
            // and notify other clients about the disconnection.
            hub.broadcast(ChatMessage.bye(username));
            hub.releaseName(username);
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

    /**
     * Closes the connection to the client.
     * This method is called to close the socket and release any associated resources.
     */
    public void disconnect() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
