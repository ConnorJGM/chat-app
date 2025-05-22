package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
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
     * @param msg The message to process.
     * @param hub The ChatServerHub instance managing all clients.
     * @param send The ClientHandler instance to send messages to the client.
     * @return true if the message was a command, false otherwise.
     *         If the message was a command, it will be processed and a response will be sent to the client.
     */
    public static boolean command(ChatMessage msg, ChatServerHub hub, ClientHandler send) {
        String body = msg.getBody().trim().toLowerCase();

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
            send.send(ChatMessage.of("Server", helpText));
            return true;
        }

        // Check if the message is a command.
        // The command format is "start number game".
        if ("start number game".equalsIgnoreCase(body)) {
            String startMsg = hub.startNumberGame(msg.getUser());
            hub.broadcast(ChatMessage.of("Server", startMsg));
            return true;
        }

        // Check for a guess in the number game.
        // If the game is active and the message is a number, check the guess.
        if (hub.hasActiveNumberGame() && body.matches("\\d+")) {
            int guess = Integer.parseInt(body);
            String reply = hub.checkGuess(msg.getUser(), guess);
            if (reply != null) {
                hub.broadcast(ChatMessage.of("Server", reply));
                return true;
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
            return true;
        }

        // Check for a vote in the poll.
        // If the poll is active and the message is a number, cast the vote.
        if (hub.hasActivePoll() && body.matches("\\d+")) {
            int vote = Integer.parseInt(body);
            String voteReply = hub.vote(msg.getUser(), vote);
            hub.broadcast(ChatMessage.of("Server", voteReply));
            return true;
        }

        // Check if the message is a command to finish the poll.
        // The command format is "finish poll".
        if ("finish poll".equalsIgnoreCase(body)) {
            String reply = hub.finish(msg.getUser());
            hub.broadcast(ChatMessage.of("Server", reply));
            return true;
        }

        // Check if the message is a command to list users.
        // The command format is "list users".
        if ("list users".equalsIgnoreCase(body)) {
            var users = hub.getUsernames();
            send.send(ChatMessage.userList(users));
            return true;
        }

        // Check if the message is a command to quit.
        // The command format is "quit".
        if ("quit".equalsIgnoreCase(body)) {
            send.send(ChatMessage.of("Server", "Goodbye!"));
            if (send instanceof WebHandler webHandler) {
                webHandler.close();
            }
        }
        // If the message is not a command, return false.
        // This indicates that the message should be processed as a regular chat message.
        return false;
    }
}
