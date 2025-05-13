// ChatServerHub.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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

    // Intialises "HISTORY_SIZE" variable and set it to 100 lines.
    private static final int HISTORY_SIZE = 100;

    // Creates double-ended queue to add and remove messages.
    private final Deque<ChatMessage> history = new ArrayDeque<>(HISTORY_SIZE);

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
        listClients();
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
        listClients();
    }

    // Sends a list of connected clients to all clients.
    // This method creates a new ChatMessage object with the type "userList"
    private void listClients() {
        ChatMessage list = ChatMessage.userList(getUsernames());
        clients.forEach(c -> c.send(list));
    }

    /**
     * Broadcasts a message to all connected clients.
     * This method is called when a client sends a message to the server.
     * It appends the message to the log and sends it to all connected clients.
     * History is maintained for the last 100 messages.
     * @param msg The message to broadcast.
     */
    void broadcast(ChatMessage msg) {
        if (history.size() == HISTORY_SIZE) {
            history.removeFirst();

        }
        history.addLast(msg);

        // Append the message to the log file.
        append(msg.toJson());

        // Send the message to all clients.
        clients.forEach(c -> c.send(msg));
    }

    /**
     * Returns the chat history.
     * This method returns a copy of the chat history, which is a list of messages.
     * The history is limited to the last 100 messages.
     * @return A list of chat messages representing the chat history.
     */
    List<ChatMessage> getHistory() {
        return List.copyOf(history);
    }

    // Returns the number of connected users.
    // This method returns the size of the set of connected clients.
    int userCount() {
        return clients.size();
    }

    // Returns a list of usernames of connected clients.
    // This method returns a sorted list of usernames obtained from the set of connected clients.
    List<String> getUsernames() {
        return clients.stream()
                      .map(ClientHandler::getUsername)
                      .sorted(String::compareToIgnoreCase)
                      .toList();
    }

    /**
     * Appends a line to the log file.
     * This method is called to log events such as client connections and messages.
     * It uses the Files.writeString method to write the line to the log file.
     * @param line The line to append to the log file.
     */
    public static void append(String line) {
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
