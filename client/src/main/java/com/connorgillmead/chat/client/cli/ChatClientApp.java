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

    private List<String> lastRoster = new ArrayList<>();
    private boolean rosterShown;

    private final ClientConfig clientConfig;
    private final Scanner scanner;

    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private BlockingQueue<ChatMessage> messageQueue;
    private String authenticatedUsername;
    private String token;

    /**
     * Constructor for ChatClientApp.
     * Initialises the client with the provided configuration and scanner.
     * @param clientConfig The configuration for the chat client, including host,
     *                     port, user, and token.
     * @param scanner The Scanner object for reading user input from the console.
     *                This is used to prompt the user for input and read messages
     *                   from the console.
     */
    public ChatClientApp(ClientConfig clientConfig, Scanner scanner) {
        this.clientConfig = clientConfig;
        this.scanner = scanner;
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
            ChatClientApp clientApp = new ChatClientApp(config, scanner);
            clientApp.run();
        }
    }

    // This method runs the chat client in plain text mode.
    // It connects to the server and starts two threads: one for sending messages
    // and another for receiving messages.
    // The method takes a ClientConfig object and a Scanner object as parameters.
    // The ClientConfig object contains the host, port, user, and token information.
    private void run() {
        // Create a new ChatClient instance and connect to the server.
        // The try-with-resources statement ensures that the socket is closed properly
        // when done.
        try (ChatClient chatClient = new ChatClient(this.clientConfig.host(), this.clientConfig.port())) {

            this.socket = chatClient.socket();

            // Thread A – send messages.
            // This thread sends messages to the server.
            // It uses a PrintWriter to send text data to the server.
            this.printWriter = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            this.bufferedReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println("Connected to chat server at " + this.clientConfig.host()
                                + ":" + this.clientConfig.port());

            AuthenticationHandler authenticationHandler = new AuthenticationHandler(scanner, printWriter,
                    bufferedReader, this.clientConfig.token());
            boolean authenticated = authenticationHandler.authenticate();
            if (!authenticated) {
                System.err.println("Authentication failed. Exiting.");
                return;
            }

            this.authenticatedUsername = authenticationHandler.getAuthenticatedUsername();
            this.token = authenticationHandler.getToken();
            if (this.authenticatedUsername == null || this.authenticatedUsername.isEmpty()) {
                System.err.println("No username provided. Exiting.");
                return;
            }

            this.messageQueue = new LinkedBlockingQueue<>();
            Thread readerThread = new Thread(() -> ChatClientNet.readLoop(this.socket, this.messageQueue));
            readerThread.setDaemon(true);
            readerThread.start();

            Thread messageProcessorThread = new Thread(this::processMessages);
            messageProcessorThread.setDaemon(true);
            messageProcessorThread.start();

            // Notify the server that the user has joined the chat.
            this.printWriter.println(ChatMessage.hello(authenticatedUsername, token).toJson());

            handleInput();
            // Close the socket and release any associated resources.
        } catch (GeneralSecurityException | IOException error) {
            error.printStackTrace();
        }
    }

    /**
     * Handles user input in a loop, allowing the user to send messages, list users,
     * or quit the chat.
     * This method runs in a separate thread and processes user input from the
     * console.
     *
     * @param socket                The socket connected to the chat server.
     * @param scanner               The Scanner object for reading user input.
     * @param printWriter           The PrintWriter for sending messages to the server.
     * @param authenticatedUsername The username of the authenticated user.
     * @param token                 The authentication token for the user.
     */
    private void handleInput() {
        final int sleep = 100;
        try {
            while (!this.socket.isClosed() && this.scanner.hasNextLine()) {
                String text = this.scanner.nextLine();

                if ("quit".equalsIgnoreCase(text.trim())) {
                    if (!this.socket.isClosed()) {
                        sendByeMessageAndWait(this.authenticatedUsername, sleep);
                        this.socket.close();
                    }
                    break;
                }

                if ("list users".equalsIgnoreCase(text.trim())) {
                    System.out.println("Connected users: " + String.join(", ", this.lastRoster));
                    continue;
                }

                if (text.isEmpty()) {
                    continue;
                }
                ChatMessage message = ChatMessage.of(this.authenticatedUsername, text);
                this.printWriter.println(message.toJson());
            }
        } catch (IOException error) {
            System.err.println("Error in user input loop: " + error.getMessage());
        } finally {
            closeSocketSafely(this.authenticatedUsername, sleep);
        }
    }

    /**
     * Sends a "bye" message to the server and waits for a specified time.
     * This method is used to notify the server that the user is leaving the chat
     * before closing the socket.
     *
     * @param username    The username of the authenticated user.
     * @param sleepTime   The time to wait after sending the "bye" message before
     *                    closing the socket.
     */
    private void sendByeMessageAndWait(String username, int sleepTime) {
        ChatMessage byeMessage = ChatMessage.bye(username);
        this.printWriter.println(byeMessage.toJson());
        sleepSafely(sleepTime);
    }

    /**
     * Sleeps for a specified number of milliseconds, handling any InterruptedException
     * that may occur.
     * This method is used to pause the execution of the thread safely.
     *
     * @param milliseconds The number of milliseconds to sleep.
     */
    private static void sleepSafely(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes the socket safely and sends a "bye" message to the server.
     * This method ensures that the socket is closed properly and handles any
     * IOException that may occur during the process.
     *
     * @param socket       The socket to be closed.
     * @param printWriter  The PrintWriter used to send messages to the server.
     * @param username     The username of the authenticated user.
     * @param sleepTime    The time to wait after sending the "bye" message before closing the socket.
     */
    private void closeSocketSafely(String username, int sleepTime) {
        if (!this.socket.isClosed()) {
            try {
                sendByeMessageAndWait(username, sleepTime);
                this.socket.close();
            } catch (IOException error) {
                System.err.println("Error closing socket: " + error.getMessage());
            }
        }
    }

    /**
     * Processes messages received from the server.
     * This method runs in a separate thread and handles different types of messages
     * such as text, roster updates, and server shutdown notifications.
     *
     * @param socket                The socket connected to the chat server.
     * @param messageQueue          The queue containing messages to be processed.
     * @param authenticatedUsername The username of the authenticated user.
     */
    private void processMessages() {
        try {
            while (this.socket != null && !this.socket.isClosed()) {
                ChatMessage message = this.messageQueue.take();

                if (ChatMessage.SERVER_SHUTDOWN.equals(message.getType())) {
                    System.out.println("The server is shutting down. Exiting...");
                    this.socket.close();
                    System.exit(0);
                    return;
                }

                if (message.isKick()) {
                    System.out.println(message.getBody());
                    this.socket.close();
                    System.exit(0);
                    return;
                }

                if ("error".equals(message.getType())) {
                    System.err.println("Server error: " + message.getBody());
                    continue;
                }

                if ("roster".equals(message.getType())) {
                    this.lastRoster = message.getUserList();
                    if (!this.rosterShown && this.lastRoster.contains(this.authenticatedUsername)) {
                        System.out.println("Connected users: " + String.join(", ", this.lastRoster));
                        this.rosterShown = true;
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
            if (this.socket != null && !this.socket.isClosed()) {
                System.err.println("Error processing message or socket closed: " + error.getMessage());
            }
        } finally {
            if (this.socket != null && !this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (IOException error) {
                }
            }
        }
    }
}
