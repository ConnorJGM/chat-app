// ChatClientApp.java

package com.connorgillmead.chat.client;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Scanner;

/**
 * ChatClientApp is a simple command-line chat client that connects to a chat server.
 * The client runs in two threads: one for sending messages and another for receiving messages.
 */
public final class ChatClientApp {

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains a main method.
     */
    private ChatClientApp() {
    }

    /**
     * Main method to start the CLI client.
     * It connects to the server and starts two threads: one for sending messages and another for receiving messages.
     * @param args The first argument is the hostname, and the second argument is the port number.
     * @throws IOException If an I/O error occurs when creating the socket or transferring data.
     * @throws GeneralSecurityException If a security error occurs when creating the socket.
     *         This can happen if the SSL/TLS protocol is not supported or if the trust manager cannot be initialised.
     */
    public static void main(String[] args) throws IOException, GeneralSecurityException {

        // Create a Scanner object to read user input from the console.
        // The Scanner is created with UTF-8 encoding to support international characters.
        try (Scanner console = new Scanner(System.in, "UTF-8")) {
            CliConfig cfg = CliConfig.fromArgs(args, console);
            runPlainClient(cfg, console);
        }
    }

    // This method runs the chat client in plain text mode.
    // It connects to the server and starts two threads: one for sending messages and another for receiving messages.
    // The method takes a CliConfig object and a Scanner object as parameters.
    // The CliConfig object contains the host, port, user, and token information.
    private static void runPlainClient(CliConfig cfg, Scanner console) {
        // Create a new ChatClient instance and connect to the server.
        // The try-with-resources statement ensures that the socket is closed properly when done.
        try (ChatClient client = new ChatClient(cfg.host(), cfg.port())) {

            Socket socket = client.socket();

            // Thread A – send messages.
            // This thread sends messages to the server.
            // It uses a PrintWriter to send text data to the server.
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
                );

            BufferedReader input = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );

            System.out.println("Connected to chat server at " + cfg.host() + ":" + cfg.port());

            AuthenticationHandler authenticationHandler = new AuthenticationHandler(console, out, input, cfg.token());
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

            // Start a new thread to read messages from the server.
            CliConfig.startReader(socket, authenticatedUsername);

            // Notify the server that the user has joined the chat.
            out.println(ChatMessage.hello(authenticatedUsername, token).toJson());

            // This thread reads user input from the console and sends it to the server.
            // It runs in a loop until the user enters "exit" or an I/O error occurs.
            while (console.hasNextLine()) {
                String text = console.nextLine();

                // If the user enters "exit", notify the server and break the loop.
                // This allows the user to leave the chat gracefully.
                if ("quit".equalsIgnoreCase(text.trim())) {
                    break;
                }

                // If the user enters "list users", display the list of connected users.
                // This command is used to show the current users in the chat.
                if ("list users".equalsIgnoreCase(text.trim())) {
                    System.out.println("Connected users: " + String.join(", ", CliConfig.getLastRoster()));
                    continue;
                }

                // If the user enters nothing, continue to the next iteration.
                // This prevents sending empty messages to the server.
                if (text.isEmpty()) {
                    continue;
                }

                // Messages are sent to the server in JSON format.
                // The ChatMessage class is used to create a message object with the username and message body.
                ChatMessage msg = ChatMessage.of(authenticatedUsername, text);
                out.println(msg.toJson());
            }
        // Close the socket and release any associated resources.
        // The try-with-resources statement ensures that the socket is closed properly when done.
        // Catch any exceptions that occur during the process.
        // This includes GeneralSecurityException and IOException.
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}
