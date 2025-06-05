// CliConfig.java

package com.connorgillmead.chat.client;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

/**
 * CliConfig is a record that holds the configuration for the chat client.
 * It contains the host name, port number, user name, and access token.
 * The record is immutable, meaning that once it is created, its values cannot be changed.
 * This is useful for ensuring that the configuration remains consistent throughout the program.
 * The record is used to pass configuration information to the chat client.
 * @param host The host name of the chat server.
 * @param port The port number of the chat server.
 * @param user The user name of the client.
 * @param token The access token for authentication.
 *             This is used to verify the identity of the client connecting to the server.
 */
public record CliConfig(String host, int port, String user, String token) {

    /**
     * Default and maximum port number for the chat server.
     * The default port number is used if no port number is provided as a command-line argument.
     * The maximum port number is used to validate the port number provided by the user.
     * The port number must be between 1 and 65535.
     */
    private static final int DEFAULT_PORT = 5555;
    private static final int MAX_PORT = 65535;

    // Static variable to store the last roster of connected users.
    // This is used to keep track of the last known list of connected users.
    private static volatile List<String> lastRoster = List.of();
    private static volatile boolean intialRosterShown;

    /**
     * Returns the last received roster (list of users).
     * @return List of usernames, or null if no roster has been received yet.
     */
    public static List<String> getLastRoster() {
        return lastRoster;
    }

    /**
     * Creates a new CliConfig object from command-line arguments.
     * The method parses the command-line arguments and prompts the user for any missing values.
     * @param args Command-line arguments passed to the program.
     *             The first argument is the host name, the second is the port number,
     *            * the third is the user name, and the fourth is the access token.
     * @param console Scanner object to read user input from the console.
     *               This is used to prompt the user for any missing values.
     * @return A new CliConfig object with the provided or default values.
     *         The object contains the host name, port number, user name, and access token.
     */
    public static CliConfig fromArgs(String[] args, Scanner console) {
        // Default values for host, port, user, and token.
        // If no arguments are provided, the user is prompted for host, port, token, and username.
        String host = null;
        int    port = -1;
        String token = null;

        // Begin looping through command-line arguments.
        for (int i = 0; i < args.length;) {
            switch (args[i]) {
                case "--host" -> {
                    host = args[i + 1];
                    i += 2;
                }
                case "--port" -> {
                    port = Integer.parseInt(args[i + 1]);
                    i += 2;
                }
                case "--token" -> {
                    token = args[i + 1];
                    i += 2;
                }
                default -> {
                    port = Integer.parseInt(args[i]);
                    i += 1;
                }
            }
        }

        // If no host argument is given, prompt user for host name.
        if (host == null) {
            System.out.print("Server host [localhost]: ");
            String h = console.nextLine().trim();
            host = h.isEmpty() ? "localhost" : h;
        }

        // If no port number is given, prompt user for port number.
        if (port == -1) {
            System.out.print("Server port [" + defaultPort() + "]: ");
            String p = console.nextLine().trim();
            if (!p.isEmpty()) {
                port = Integer.parseInt(p);
            } else {
                port = defaultPort();
            }
        }

        // If no token is given, prompt user for a token.
        if (token == null) {
            System.out.print("Access token (or Enter for none): ");
            String t = console.nextLine().trim();
            token = t.isEmpty() ? null : t;
        }

        return new CliConfig(host, port, null, token);
    }

    /**
     * Validates the TUI configuration.
     * This method checks if the host, port, user, and token values are valid.
     * @param host The host name of the chat server.
     * @param port The port number of the chat server.
     * @param user The user name of the client.
     * @param token The access token for authentication.
     *             This is used to verify the identity of the client connecting to the server.
     * @return A new CliConfig object with the provided values.
     *         The object contains the host name, port number, user name, and access token.
     */
    public static CliConfig validateCreate(String host,
                                       int port,
                                       String user,
                                       String token) {
        // Validate the host, port, user, and token values.
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty.");
        }
        if (port <= 0 || port > MAX_PORT) {
            throw new IllegalArgumentException("Please enter a valid port number (1-65535).");
        }
        if (user == null || user.isEmpty()) {
            throw new IllegalArgumentException("User cannot be null or empty.");
        }
        return new CliConfig(host, port, user, token);
    }

    // Default port number getter for the chat server.
    // This is used if no port number is provided as a command-line argument.
    public static int defaultPort() {
        return DEFAULT_PORT;
    }

    /**
    * Thread B – receive messages.
    * This thread reads messages from the server and prints them to the console.
    * It runs in a separate thread to allow for concurrent message sending and receiving.
    * The thread will continue to run until the socket is closed or an I/O error occurs.
    */
    public static void startReader(Socket socket, String user) {

        // Create a new thread to read messages from the server.
        new Thread(() -> {
            try (BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                String line;

                // Read messages from the server in a loop.
                // Each message is expected to be in JSON format.
                while ((line = input.readLine()) != null) {
                    ChatMessage message = ChatMessage.fromJson(line);

                    // If the user is kicked, print the message body and exit the application.
                    // This is used to notify the user that they have been kicked from the chat.
                    if (message.isKick()) {
                        System.out.println(message.getBody());
                        System.exit(0);
                    }

                    // Exit application if authentication fails.
                    if ("error".equals(message.getType())) {
                        System.err.println("Server refused connection: " + message.getBody());
                        System.exit(1);
                    }

                    // If the message is a roster update, print the list of connected users.
                    // Otherwise, print the message body and sender.
                    if ("roster".equals(message.getType())) {
                        lastRoster = message.getUserList();
                        if (!intialRosterShown && lastRoster.contains(user)) {
                            System.out.println("Connected users: " + String.join(", ", lastRoster));
                            intialRosterShown = true;
                        }
                    } else {
                        System.out.printf("%s: %s%n", message.getUser(), message.getBody());
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.err.println("Error reading from server: " + e.getMessage());
                }
            }
        }).start();
    }
}
