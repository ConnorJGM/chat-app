// ChatServerHub.java

package com.connorgillmead.chat.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the clients connected to the chat server.
 * This class is responsible for adding and removing clients,
 * as well as broadcasting messages to all connected clients.
 */
final class ChatServerHub {
    /**
     * The path to the log file where chat messages are stored.
     * This is a static final field, meaning it is a constant value that does not change.
     */
    private static final Path LOG = Path.of("chat.log");

    /**
     * A set of connected clients.
     * This is a thread-safe set that allows concurrent access from multiple threads.
     */
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains static methods.
     * @param c The client handler to add.
     */
    void addClient(ClientHandler c) {
        clients.add(c);
        append(String.format("[%s] JOIN %s",
               Instant.now(), c.getUsername()));
    }

    /**
     * Removes a client from the chat server.
     * This method is called when a client disconnects from the server.
     * It removes the client from the set of connected clients and logs the event.
     * @param c The client handler to remove.
     */
    void removeClient(ClientHandler c) {
        clients.remove(c);
        append(String.format("[%s] LEAVE %s",
               Instant.now(), c.getUsername()));
    }

    /**
     * Broadcasts a message to all connected clients.
     * This method is called when a client sends a message to the server.
     * It appends the message to the log and sends it to all connected clients.
     * @param msg The message to broadcast.
     */
    void broadcast(ChatMessage msg) {
        append(msg.toJson());

        // Send the message to all clients
        clients.forEach(c -> c.send(msg));
    }

    /**
     * Appends a line to the log file.
     * This method is called to log events such as client connections and messages.
     * It uses the Files.writeString method to write the line to the log file.
     * @param line The line to append to the log file.
     */
    private static void append(String line) {
        try {
            Files.writeString(
                LOG,
                line + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("LOG ERROR: " + e.getMessage());
        }
    }
}
