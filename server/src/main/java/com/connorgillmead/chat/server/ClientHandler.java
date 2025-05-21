// ClientHandler.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

/**
 * Handles communication with a single client.
 * This class is responsible for reading messages from the client,
 * processing them, and sending responses back to the client.
 * It implements the Runnable interface to allow it to be run in a separate thread.
 */
final class ClientHandler implements Runnable {
    // Instance variables for the ClientHandler class.
    // The beginIndex is used to extract the question from the poll command.
    private static final int BEGIN_INDEX = 11;
    private final Socket socket;
    private final ChatServerHub hub;
    private final String username;
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
     * @return The socket of the client.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            // Get the output stream for sending messages to the client.
            // The PrintWriter is used to send text data to the client.
            // The 'true' argument enables auto-flushing (output stream flushed).
            out = new PrintWriter(socket.getOutputStream(), true);

            // Display chat history to the client.
            hub.getHistory().forEach(this::send);

            // Create new thread for the client and add it to the hub.
            hub.addClient(this);

            // Send the list of connected users to the client.
            send(ChatMessage.userList(hub.getUsernames()));

            // Read messages from the client and broadcast them to all clients.
            // This loop continues until the client disconnects or an I/O error occurs.
            String line;
            while ((line = in.readLine()) != null) {
                ChatMessage msg = ChatMessage.fromJson(line);
                String body = msg.getBody().trim();

                // Check if the message is a command.
                // The command format is "start number game".
                if ("start number game".equalsIgnoreCase(body)) {
                    String startMsg = hub.startNumberGame(msg.getUser());
                    hub.broadcast(ChatMessage.of("Server", startMsg));
                    continue;
                }

                // Check for a guess in the number game.
                // If the game is active and the message is a number, check the guess.
                if (hub.hasActiveNumberGame() && body.matches("\\d+")) {
                    int guess = Integer.parseInt(body);
                    String reply = hub.checkGuess(msg.getUser(), guess);
                    if (reply != null) {
                        hub.broadcast(ChatMessage.of("Server", reply));
                        continue;
                    }
                }

                // Check if the message is a command to start a poll.
                // The command format is "start poll: question | option1 | option2 | ...".
                if (body.toLowerCase().startsWith("start poll:")) {
                    String[] parts = body.substring(BEGIN_INDEX).split("\\|");
                    String question = parts[0].trim();
                    String[] options = Arrays.stream(parts, 1, parts.length)
                            .map(String::trim)
                            .toArray(String[]::new);
                    String reply = hub.startPoll(msg.getUser(), question, options);
                    hub.broadcast(ChatMessage.of("Server", reply));
                    continue;
                }

                // Check for a vote in the poll.
                // If the poll is active and the message is a number, cast the vote.
                if (hub.hasActivePoll() && body.matches("\\d+")) {
                    int vote = Integer.parseInt(body);
                    String voteReply = hub.vote(msg.getUser(), vote);
                    hub.broadcast(ChatMessage.of("Server", voteReply));
                    continue;
                }

                // Check if the message is a command to finish the poll.
                // The command format is "finish poll".
                if ("finish poll".equalsIgnoreCase(body)) {
                    String reply = hub.finish(msg.getUser());
                    hub.broadcast(ChatMessage.of("Server", reply));
                    continue;
                }

                // Check if the message is a help command.
                // The command format is "help".
                if ("help".equalsIgnoreCase(body)) {
                    String helpText = """
                            Available commands:
                            • help                                            - Show this help message.
                            • start number game                               - Start a number guessing game.
                            • start poll: question | option1 | option2 | ...  - Start a poll.
                            • finish poll                                     - Finish the current poll.
                            • list users                                      - List all connected users.
                            • quit                                            - Disconnect from the chat.
                            """;
                    // Send the help message to the client.
                    // The help message is formatted as a JSON object.
                    send(ChatMessage.of("Server", helpText));
                    continue;
                }

                // Check if the message is a command to list users.
                // The command format is "list users".
                if ("list users".equalsIgnoreCase(body)) {
                    var users = hub.getUsernames();
                    send(ChatMessage.userList(users));
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
}
