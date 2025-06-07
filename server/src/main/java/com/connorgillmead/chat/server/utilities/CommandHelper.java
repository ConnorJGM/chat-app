// CommandHelper.java

package com.connorgillmead.chat.server.utilities;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.tcp.ChatServerHub;
import com.connorgillmead.chat.server.tcp.ClientHandler;
import com.connorgillmead.chat.server.websocket.WebHandler;
import java.util.Arrays;

/**
 * CommandHelper is a utility class that handles command messages from the client.
 * It processes commands such as help, start number game, start poll, finish poll, list users, and quit.
 * This class is not meant to be instantiated; it only contains static methods.
 */
public final class CommandHelper {

    // The beginIndex is used to extract the question from the poll command.
    private static final int BEGIN_INDEX = 11;

    private CommandHelper() {
        // Private constructor to prevent instantiation.
    }

    /**
     * Handles the command messages from the client.
     * This method checks if the message is a command and processes it accordingly.
     * @param message The message to process.
     * @param hub The ChatServerHub instance managing all clients.
     * @param send The ClientHandler instance to send messages to the client.
     * @return true if the message was a command, false otherwise.
     *         If the message was a command, it will be processed and a response will be sent to the client.
     */
    public static boolean command(ChatMessage message, ChatServerHub hub, ClientHandler send) {
        String body = message.getBody().trim().toLowerCase();

        // Check for a guess in the number game.
        if (hub.hasActiveNumberGame() && body.matches("\\d+")) {
            int guess = Integer.parseInt(body);
            String reply = hub.checkGuess(message.getUser(), guess);
            if (reply != null) {
                hub.broadcast(ChatMessage.of("Server", reply));
                return true;
            }
        // Check if the message is a command to start a poll.
        } else if (body.startsWith("start poll:")) {
            String[] parts = body.substring(BEGIN_INDEX).split("\\|");
            String question = parts[0].trim();
            String[] options = Arrays.stream(parts, 1, parts.length)
                    .map(String::trim)
                    .toArray(String[]::new);
            String reply = hub.startPoll(message.getUser(), question, options);
            hub.broadcast(ChatMessage.of("Server", reply));
            return true;
        // Check for a vote in the poll.
        } else if (hub.hasActivePoll() && body.matches("\\d+")) {
            int vote = Integer.parseInt(body);
            String voteReply = hub.vote(message.getUser(), vote);
            hub.broadcast(ChatMessage.of("Server", voteReply));
            return true;
        } else {
            switch (body) {
                // Check if the message is a help command.
                // The command format is "help".
                case "help" -> {
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
                    send.send(ChatMessage.of("Server", helpText));
                    return true;
                }
                // Check if the message is a command.
                // The command format is "start number game".
                case "start number game" -> {
                    String startMsg = hub.startNumberGame(message.getUser());
                    hub.broadcast(ChatMessage.of("Server", startMsg));
                    return true;
                }
                // Check if the message is a command to finish the poll.
                // The command format is "finish poll".
                case "finish poll" -> {
                    String reply = hub.finish(message.getUser());
                    hub.broadcast(ChatMessage.of("Server", reply));
                    return true;
                }
                // Check if the message is a command to list users.
                // The command format is "list users".
                case "list users" -> {
                    var users = hub.getUsernames();
                    send.send(ChatMessage.userList(users));
                    return true;
                }
                // Check if the message is a command to quit.
                // The command format is "quit".
                case "quit" -> {
                    if (send instanceof WebHandler web) {
                        web.close();
                    } else {
                        send.send(ChatMessage.of("Server", "Goodbye!"));
                    }
                    return true;
                }
                default -> {
                    // If the message is not a command, return false.
                    // This indicates that the message should be processed as a regular chat message.
                    return false;
                }
            }
        }
        // If the message is not a command, return false.
        // This indicates that the message should be processed as a regular chat message.
        return false;
    }
}
