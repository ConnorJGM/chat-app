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

    // Creates a thread-safe set to store connected clients.
    // This set allows concurrent access from multiple threads.
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    // Creates a thread-safe set to store usernames in use.
    // This set allows concurrent access from multiple threads.
    private final Set<String> namesInUse = ConcurrentHashMap.newKeySet();

    // Current number game instance.
    // This instance is used to manage the state of the number guessing game.
    private NumberGame currentNumberGame;

    // Current poll instance.
    // This instance is used to manage the state of the poll.
    private Poll currentPoll;

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains static methods.
     * @param c The client handler to add.
     */
    void addClient(ClientHandler c) {
        clients.add(c);
        append(String.format("[%s] JOIN %s",
               Instant.now(), c.getUsername()));
        broadcast(ChatMessage.userList(getUsernames()));
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
        broadcast(ChatMessage.userList(getUsernames()));
    }

    /**
     * Reserves a username for a client.
     * This method is called when a client connects to the server.
     * @param user The username to reserve.
     * This method adds the username to the set of names in use.
     * @return True if the name was successfully reserved, false if it was already in use.
     * This method checks if the username is already in use by another client.
     */
    boolean reserveName(String user) {
        return namesInUse.add(user.toLowerCase());
    }

    /**
     * Releases a reserved name.
     * This method is called when a client disconnects or changes their username.
     * @param user The username to release.
     * This method removes the username from the set of names in use.
     */
    void releaseName(String user) {
        if (user != null) {
            namesInUse.remove(user.toLowerCase());
        }
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

        // Send ALL messages to all clients
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

    /**
     * Starts a number guessing game.
     * This method is called when a player wants to start a new game.
     * @param owner The name of the player who starts the game.
     * This method creates a new NumberGame instance and sets it as the current game.
     * @return A message indicating the game has started or an error message if a game is already in progress.
     * This method checks if there is already an active game and returns an error message if so.
     */
    public synchronized String startNumberGame(String owner) {
        if (currentNumberGame != null && currentNumberGame.isActive()) {
            return "A number game is already in progress.";
        }
        currentNumberGame = new NumberGame(owner);
        return currentNumberGame.getStartMessage();
    }

    /**
     * Handles a player's guess in the number guessing game.
     * This method is called when a player makes a guess.
     * @param player The name of the player making the guess.
     * @param guess The player's guess.
     * This method checks the player's guess against the target number.
     * @return A message indicating whether the guess is too low, too high, or correct.
     * If the guess is correct, the game is marked as inactive.
     */
    public synchronized String checkGuess(String player, int guess) {
        if (currentNumberGame == null || !currentNumberGame.isActive()) {
            return null;
        }
        String reply = currentNumberGame.checkGuess(player, guess);
        if (!currentNumberGame.isActive()) {
            currentNumberGame = null; // Reset the game instance
        }
        return reply;
    }

    public synchronized boolean hasActiveNumberGame() {
        return currentNumberGame != null && currentNumberGame.isActive();
    }

    /**
     * Starts a poll.
     * This method is called when a player wants to start a new poll.
     * @param owner The name of the player who starts the poll.
     * This method creates a new Poll instance and sets it as the current poll.
     * @param question The question for the poll.
     * @param options The options for the poll.
     * This method checks if there is already an active poll and returns an error message if so.
     * @return A message indicating the poll has started or an error message if a poll is already in progress.
     * This method checks if there is already an active poll and returns an error message if so.
     */
    public synchronized String startPoll(String owner, String question, String... options) {
        if (currentPoll != null && currentPoll.isActive()) {
            return "A poll is already in progress.";
        }
        currentPoll = new Poll(owner, question, options);
        return currentPoll.getStartMessage();
    }

    /**
     * Handles a player's vote in the poll.
     * This method is called when a player votes in the poll.
     * @param voter The name of the player making the vote.
     * @param option The option chosen by the player.
     * This method checks if the poll is active and if the option is valid.
     * @return A message indicating the result of the vote or
     * an error message if the poll is closed or the option is invalid.
     * This method checks if the poll is active and if the option is valid.
     */
    public synchronized String vote(String voter, int option) {
        if (currentPoll == null || !currentPoll.isActive()) {
            return "No active poll.";
        }
        return currentPoll.vote(voter, option);
    }

    /**
     * Finishes the poll.
     * This method is called when the poll owner wants to finish the poll.
     * @return A message indicating the results of the poll or an error message if there is no active poll.
     * This method checks if the poll is active and if the owner is the one who started the poll.
     */
    public synchronized String finish(String requester) {
        if (currentPoll == null || !currentPoll.isActive()) {
            return "No active poll.";
        }
        if (!currentPoll.getOwner().equals(requester)) {
            return "Only " + currentPoll.getOwner() + " can finish the poll.";
        }
        String results = currentPoll.finish();
        currentPoll = null;
        return results;
    }

    // Checks if there is an active poll.
    // This method checks if the current poll is not null and if it is active.
    public synchronized boolean hasActivePoll() {
        return currentPoll != null && currentPoll.isActive();
    }
}
