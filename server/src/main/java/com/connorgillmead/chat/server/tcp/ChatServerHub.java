// ChatServerHub.java

package com.connorgillmead.chat.server.tcp;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.database.MessageDBHandler;
import com.connorgillmead.chat.server.utilities.NumberGame;
import com.connorgillmead.chat.server.utilities.Poll;
import com.connorgillmead.chat.server.websocket.WebHandler;
import com.connorgillmead.chat.server.websocket.WebSocketChatEndpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the clients connected to the chat server.
 * This class is responsible for adding and removing clients,
 * as well as broadcasting messages to all connected clients.
 */
public final class ChatServerHub {
    /**
     * The path to the log file where chat messages are stored.
     * This is a static final field, meaning it is a constant value that does not
     * change.
     */
    private static final Path LOG = Path.of("chat.log");

    // Intialises "HISTORY_SIZE" variable and set it to 100 lines.
    private static final int HISTORY_SIZE = 100;

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

    // Creates a token for the chat server.
    // This token is used to authenticate clients and manage their sessions.
    private String token;

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains static methods.
     *
     * @param c The client handler to add.
     */
    public void addClient(ClientHandler c) {
        clients.add(c);
        append(String.format("[%s] JOIN %s",
                Instant.now(), c.getUsername()));
        broadcast(ChatMessage.userList(getUsernames()));
    }

    /**
     * Removes a client from the chat server.
     * This method is called when a client disconnects from the server.
     * It removes the client from the set of connected clients and logs the event.
     *
     * @param c The client handler to remove.
     */
    public void removeClient(ClientHandler c) {
        clients.remove(c);
        append(String.format("[%s] LEAVE %s",
                Instant.now(), c.getUsername()));
        broadcast(ChatMessage.userList(getUsernames()));
    }

    /**
     * Reserves a username for a client.
     * This method is called when a client connects to the server.
     *
     * @param user The username to reserve.
     *             This method adds the username to the set of names in use.
     * @return True if the name was successfully reserved, false if it was already
     *         in use.
     *         This method checks if the username is already in use by another
     *         client.
     */
    public synchronized boolean reserveName(String user) {
        for (String u : namesInUse) {
            if (u.equalsIgnoreCase(user)) {
                return false;
            }
        }
        namesInUse.add(user);
        return true;
    }

    /**
     * Sets the token for the chat server.
     * This method is called to set the token used for authentication.
     *
     * @param token The token to set for the chat server.
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Gets the token for the chat server.
     * This method is called to retrieve the token used for authentication.
     *
     * @return The token for the chat server.
     */
    public String getToken() {
        return token;
    }

    /**
     * Releases a reserved name.
     * This method is called when a client disconnects or changes their username.
     *
     * @param user The username to release.
     *             This method removes the username from the set of names in use.
     */
    public synchronized void releaseName(String user) {
        if (user != null) {
            namesInUse.removeIf(u -> u.equalsIgnoreCase(user));
        }
    }

    /**
     * Broadcasts a message to all connected clients.
     * This method is called when a client sends a message to the server.
     * It appends the message to the log and sends it to all connected clients.
     * History is maintained for the last 100 messages.
     *
     * @param msg The message to broadcast.
     */
    public void broadcast(ChatMessage msg) {
        // Append the message to the log file if not "roster".
        if (!"roster".equals(msg.getType())) {
            append(msg.toJson());
        }

        // Send to all TCP clients.
        for (ClientHandler handler : clients) {
            if (!(handler instanceof WebHandler)) {
                handler.send(msg);
            }
        }
        // Send to all WebSocket clients.
        WebSocketChatEndpoint.sendToAll(msg);
    }

    /**
     * Shuts down the server and disconnects all clients.
     * This method is called when the server is shutting down.
     * It sends a shutdown message to all connected clients and disconnects them.
     *
     * @param reason The reason for the server shutdown.
     *               This method logs the shutdown reason and sends it to all clients.
     */
    public void serverShutdown(String reason) {
        ChatMessage shutdownMessage = ChatMessage.serverShutdown(reason);
        System.out.println("Broadcasting server shutdown: " + reason);
        append(String.format("[%s] SERVER_SHUTDOWN %s", Instant.now(), reason));

        List<ClientHandler> clientListCopy = List.copyOf(clients);

        for (ClientHandler client : clientListCopy) {
            System.out.println("Server: Shutting down message to: " + client.getUsername());
            client.send(shutdownMessage);
        }

        try {
            final int shutdownDelay = 200;
            Thread.sleep(shutdownDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Server shutdown interrupted: " + e.getMessage());
        }

        System.out.println("Disconnecting all clients.");
        for (ClientHandler client : clientListCopy) {
            if (client instanceof WebHandler web) {
                web.close();
            } else {
                client.disconnect();
            }
        }
    }

    /**
     * Returns the chat history.
     * This method returns a copy of the chat history, which is a list of messages.
     * The history is limited to the last 100 messages.
     *
     * @return A list of chat messages representing the chat history.
     */
    public List<ChatMessage> getHistory() {
        return MessageDBHandler.getMessages(HISTORY_SIZE);
    }

    /**
     * Sends the chat history to a specific client.
     * This method is called to send the chat history to a client when they connect
     * or request it.
     *
     * @param client The client handler to send the history to.
     *               This method iterates through the chat history and sends each
     *               message to the specified client.
     */
    public void sendHistory(ClientHandler client) {
        List<ChatMessage> history = getHistory();
        for (ChatMessage historicalMessages : history) {
            client.send(historicalMessages);
        }
    }

    // Returns the number of connected users.
    // This method returns the size of the set of connected clients.
    public int userCount() {
        return clients.size();
    }

    // Returns a list of usernames of connected clients.
    // This method returns a sorted list of usernames obtained from the set of
    // connected clients.
    public List<String> getUsernames() {
        return namesInUse.stream()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    /**
     * Appends a line to the log file.
     * This method is called to log events such as client connections and messages.
     * It uses the Files.writeString method to write the line to the log file.
     *
     * @param line The line to append to the log file.
     */
    public static void append(String line) {
        try {
            Files.writeString(
                    LOG,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("LOG ERROR: " + e.getMessage());
        }
    }

    /**
     * Starts a number guessing game.
     * This method is called when a player wants to start a new game.
     *
     * @param owner The name of the player who starts the game.
     *              This method creates a new NumberGame instance and sets it as the
     *              current game.
     * @return A message indicating the game has started or an error message if a
     *         game is already in progress.
     *         This method checks if there is already an active game and returns an
     *         error message if so.
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
     *
     * @param player The name of the player making the guess.
     * @param guess  The player's guess.
     *               This method checks the player's guess against the target
     *               number.
     * @return A message indicating whether the guess is too low, too high, or
     *         correct.
     *         If the guess is correct, the game is marked as inactive.
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
     *
     * @param owner    The name of the player who starts the poll.
     *                 This method creates a new Poll instance and sets it as the
     *                 current poll.
     * @param question The question for the poll.
     * @param options  The options for the poll.
     *                 This method checks if there is already an active poll and
     *                 returns an error message if so.
     * @return A message indicating the poll has started or an error message if a
     *         poll is already in progress.
     *         This method checks if there is already an active poll and returns an
     *         error message if so.
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
     *
     * @param voter  The name of the player making the vote.
     * @param option The option chosen by the player.
     *               This method checks if the poll is active and if the option is
     *               valid.
     * @return A message indicating the result of the vote or
     *         an error message if the poll is closed or the option is invalid.
     *         This method checks if the poll is active and if the option is valid.
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
     *
     * @return A message indicating the results of the poll or an error message if
     *         there is no active poll.
     *         This method checks if the poll is active and if the owner is the one
     *         who started the poll.
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

    /**
     * Kicks a user from the chat server.
     * This method is called to disconnect a user by their username.
     *
     * @param username The username of the user to kick.
     *                 This method iterates through all connected clients and sends
     *                 a kick message to the specified user.
     */
    public synchronized void kickUser(String username) {
        for (ClientHandler client : clients) {
            if (client.getUsername().equalsIgnoreCase(username)) {
                client.send(ChatMessage.kick("You have been kicked from the chat."));
                if (client instanceof WebHandler web) {
                    web.close();
                } else {
                    client.disconnect();
                }
                clients.remove(client);
                break;
            }
        }
    }
}
