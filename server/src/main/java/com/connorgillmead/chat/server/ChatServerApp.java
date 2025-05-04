// ChatServerHub.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Scanner;

/**
 * Starts the TCP listener and spins up a thread for each client.
 */
public final class ChatServerApp {

    /**
     * The expected token for authentication.
     * This token is used to verify the identity of the client connecting to the server.
     * It is set via command-line arguments or prompted from the user.
     * If no token is provided, the server will accept any client connection.
     */
    private static String expectedToken;

    /**
     * Default port number for the chat server.
     * This is used if no port number is provided as a command-line argument.
     */
    private static final int DEFAULT_PORT = 5555;

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains a main method.
     */
    private ChatServerApp() {
    }

    /**
     * Main method to start the server.
     * @param args Command line arguments. The first argument is the port number (default is 5555).
     * @throws IOException If an I/O error occurs when creating the server socket or accepting a connection.
     */
    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;

        // Begin looping through command-line arguments.
        for (int i = 0; i < args.length;) {
            String a = args[i];

            // If current argument is "--token", check for value.
            if ("--token".equals(a) && i + 1 < args.length) {
                expectedToken = args[i + 1];
                i += 2;
            } else {
                // If the user provides a port, parse it as an integer.
                // If the port is invalid, prompt the user to enter a valid number.
                try {
                    port = Integer.parseInt(a);
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number. Please enter a valid number.");
                    return;
                }
            }
        }

        // Create a Scanner to read user input from the console.
        try (Scanner console = new Scanner(System.in, "UTF-8")) {

            // Check if the expectedToken has not been set (null).
            if (expectedToken == null) {
                System.out.print("Access token (or press Enter for none): ");
                String t = console.nextLine().trim();
                expectedToken = t.isEmpty() ? null : t;
            }
        }

        try (ChatServer tcp = new ChatServer(port)) {
            System.out.println("Server listening on port: " + port);

            // Create a new ChatServerHub instance to manage connected clients.
            // The ChatServerHub is responsible for broadcasting messages to all connected clients.
            ChatServerHub hub = new ChatServerHub();

            // Start the HTTP server for status monitoring.
            // The StatusHttpServer provides a simple HTTP interface to check the server status and connected users.
            StatusHttpServer.start(hub);

            /*
             * Thread A – accept connections.
             * This thread accepts incoming connections from clients and starts a new thread for each client.
             * It reads the first line of input from the client to get the username,
             * and then creates a new ClientHandler thread to handle the client.
             */
            while (true) {
                Socket socket = tcp.awaitConnection();

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                String firstLine = in.readLine();
                if (firstLine == null) {
                    socket.close();
                    continue;
                }

                // Parse the first line to get the username.
                // The first line is expected to be a JSON string representing a ChatMessage.
                ChatMessage hello = ChatMessage.fromJson(firstLine);

                // Authenticate expected token by actual token.
                // Appends authentication failure message if invalid to "chat.log".
                // Closes socket if token does not equal expectedToken.
                if (expectedToken != null && !expectedToken.equals(hello.getToken())) {
                    ChatServerHub.append(String.format("[%s] AUTH_FAIL from %s",
                            Instant.now(), socket.getRemoteSocketAddress()));
                    socket.close();
                    continue;
                }

                // Get the username from the hello message.
                // The username is extracted from the ChatMessage object.
                String username = hello.getUser();

                // Print the connection message to the console.
                // This message indicates that a new client has connected to the server.
                System.out.println(username + " connected on " + socket);

                // Add the client to the hub and broadcast the hello message.
                hub.broadcast(hello);

                // Thread B – handle client
                // This thread handles the client connection and processes messages.
                new Thread(new ClientHandler(socket, hub, username)).start();
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}
