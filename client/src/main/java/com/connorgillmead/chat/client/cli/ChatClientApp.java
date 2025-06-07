// ChatClientApp.java

package com.connorgillmead.chat.client.cli;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ChatClientApp is a simple command-line chat client that connects to a chat
 * server.
 * The client runs in two threads: one for sending messages and another for
 * receiving messages.
 */
public final class ChatClientApp {

    private static List<String> lastRoster = new ArrayList<>();
    private static boolean rosterShown;

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains a main method.
     */
    private ChatClientApp() {
    }

    /**
     * Main method to start the CLI client.
     * It connects to the server and starts two threads: one for sending messages
     * and another for receiving messages.
     *
     * @param arguments The first argument is the hostname, and the second argument
     *                  is the port number.
     * @throws IOException              If an I/O error occurs when creating the
     *                                  socket or transferring data.
     * @throws GeneralSecurityException If a security error occurs when creating the
     *                                  socket.
     *                                  This can happen if the SSL/TLS protocol is
     *                                  not supported or if the trust manager cannot
     *                                  be initialised.
     */
    public static void main(String[] arguments) throws IOException, GeneralSecurityException {

        // Create a Scanner object to read user input from the scanner.
        // The Scanner is created with UTF-8 encoding to support international
        // characters.
        try (Scanner scanner = new Scanner(System.in, "UTF-8")) {
            ClientConfig config = ClientConfig.fromArgs(arguments, scanner);
            runPlainClient(config, scanner);
        }
    }

    // This method runs the chat client in plain text mode.
    // It connects to the server and starts two threads: one for sending messages
    // and another for receiving messages.
    // The method takes a ClientConfig object and a Scanner object as parameters.
    // The ClientConfig object contains the host, port, user, and token information.
    private static void runPlainClient(ClientConfig config, Scanner scanner) {
        // Create a new ChatClient instance and connect to the server.
        // The try-with-resources statement ensures that the socket is closed properly
        // when done.
        try (ChatClient client = new ChatClient(config.host(), config.port())) {

            Socket socket = client.socket();

            // Thread A – send messages.
            // This thread sends messages to the server.
            // It uses a PrintWriter to send text data to the server.
            PrintWriter printWriter = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println("Connected to chat server at " + config.host() + ":" + config.port());

            AuthenticationHandler authenticationHandler = new AuthenticationHandler(scanner, printWriter,
                    bufferedReader, config.token());
            boolean authenticated = authenticationHandler.authenticate();
            if (!authenticated) {
                System.err.println("Authentication failed. Exiting.");
                return;
            }

            String authenticatedUsername = authenticationHandler.getAuthenticatedUsername();
            String token = authenticationHandler.getToken();
            if (authenticatedUsername == null || authenticatedUsername.isEmpty()) {
                System.err.println("No username provided. Exiting.");
                return;
            }

            BlockingQueue<ChatMessage> messageQueue = new LinkedBlockingQueue<>();
            Thread readerThread = new Thread(() -> ChatClientNet.readLoop(socket, messageQueue));
            readerThread.setDaemon(true);
            readerThread.start();

            Thread messageProcessorThread = new Thread(() -> {
                processMessages(socket, messageQueue, authenticatedUsername);
            });
            messageProcessorThread.setDaemon(true);
            messageProcessorThread.start();

            // Notify the server that the user has joined the chat.
            printWriter.println(ChatMessage.hello(authenticatedUsername, token).toJson());

            // This thread reads user input from the scanner and sends it to the server.
            // It runs in a loop until the user enters "exit" or an I/O error occurs.
            while (!socket.isClosed() && scanner.hasNextLine()) {
                String text = scanner.nextLine();

                // If the user enters "exit", notify the server and break the loop.
                // This allows the user to leave the chat gracefully.
                if ("quit".equalsIgnoreCase(text.trim())) {
                    socket.close();
                    break;
                }

                // If the user enters "list users", display the list of connected users.
                // This command is used to show the current users in the chat.
                if ("list users".equalsIgnoreCase(text.trim())) {
                    System.out.println("Connected users: " + String.join(", ", lastRoster));
                    continue;
                }

                // If the user enters nothing, continue to the next iteration.
                // This prevents sending empty messages to the server.
                if (text.isEmpty()) {
                    continue;
                }

                // Messages are sent to the server in JSON format.
                // The ChatMessage class is used to create a message object with the username
                // and message body.
                ChatMessage message = ChatMessage.of(authenticatedUsername, text);
                printWriter.println(message.toJson());
            }
            // Close the socket and release any associated resources.
            // The try-with-resources statement ensures that the socket is closed properly
            // when done.
            // Catch any exceptions that occur during the process.
            // This includes GeneralSecurityException and IOException.
        } catch (GeneralSecurityException | IOException error) {
            error.printStackTrace();
        }
    }

    /**
     * Processes messages received from the server.
     * This method runs in a separate thread and handles different types of messages
     * such as text, roster updates, and server shutdown notifications.
     *
     * @param socket              The socket connected to the chat server.
     * @param messageQueue        The queue containing messages to be processed.
     * @param authenticatedUsername The username of the authenticated user.
     */
    private static void processMessages(Socket socket, BlockingQueue<ChatMessage> messageQueue,
            String authenticatedUsername) {
        try {
            while (!socket.isClosed()) {
                ChatMessage message = messageQueue.take();

                if (ChatMessage.SERVER_SHUTDOWN.equals(message.getType())) {
                    System.out.println("The server is shutting down. Exiting...");
                    socket.close();
                    System.exit(0);
                    return;
                }

                if (message.isKick()) {
                    System.out.println(message.getBody());
                    socket.close();
                    System.exit(0);
                    return;
                }

                if ("error".equals(message.getType())) {
                    System.err.println("Server error: " + message.getBody());
                    continue;
                }

                if ("roster".equals(message.getType())) {
                    lastRoster = message.getUserList();
                    if (!rosterShown && lastRoster.contains(authenticatedUsername)) {
                        System.out.println("Connected users: " + String.join(", ", lastRoster));
                        rosterShown = true;
                    }
                    continue;
                }

                switch (message.getType()) {
                    case "hello", "text", "bye" -> {
                        if (message.getUser() != null && message.getBody() != null) {
                            System.out.printf("%s: %s%n", message.getUser(), message.getBody());
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Message processing was interrupted.");
        } catch (IOException error) {
            if (!socket.isClosed()) {
                System.err.println("Error processing message or socket closed: " + error.getMessage());
            }
        } finally {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException error) {
                }
            }
        }
    }
}
