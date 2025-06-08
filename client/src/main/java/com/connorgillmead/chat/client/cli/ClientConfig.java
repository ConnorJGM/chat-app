// ClientConfig.java

package com.connorgillmead.chat.client.cli;

import java.util.Scanner;

/**
 * ClientConfig is a record that holds the configuration for the chat client.
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
public record ClientConfig(String host, int port, String user, String token) {

    /**
     * Default and maximum port number for the chat server.
     * The default port number is used if no port number is provided as a command-line argument.
     * The maximum port number is used to validate the port number provided by the user.
     * The port number must be between 1 and 65535.
     */
    private static final int DEFAULT_PORT = 5555;
    private static final int MAX_PORT = 65535;

    /**
     * Creates a new ClientConfig object from command-line arguments.
     * The method parses the command-line arguments and prompts the user for any missing values.
     * @param arguments Command-line arguments passed to the program.
     *             The first argument is the host name, the second is the port number,
     *            * the third is the user name, and the fourth is the access token.
     * @param scanner Scanner object to read user input from the console.
     *               This is used to prompt the user for any missing values.
     * @return A new ClientConfig object with the provided or default values.
     *         The object contains the host name, port number, user name, and access token.
     */
    public static ClientConfig fromArgs(String[] arguments, Scanner scanner) {
        // Default values for host, port, user, and token.
        // If no arguments are provided, the user is prompted for host, port, token, and username.
        String host = null;
        int    port = -1;
        String token = null;

        // Begin looping through command-line arguments.
        for (int i = 0; i < arguments.length;) {
            switch (arguments[i]) {
                case "--host" -> {
                    host = arguments[i + 1];
                    i += 2;
                }
                case "--port" -> {
                    port = Integer.parseInt(arguments[i + 1]);
                    i += 2;
                }
                case "--token" -> {
                    token = arguments[i + 1];
                    i += 2;
                }
                default -> {
                    port = Integer.parseInt(arguments[i]);
                    i += 1;
                }
            }
        }

        // If no host argument is given, prompt user for host name.
        if (host == null) {
            System.out.print("Server host [localhost]: ");
            String hostCheck = scanner.nextLine().trim();
            host = hostCheck.isEmpty() ? "localhost" : hostCheck;
        }

        // If no port number is given, prompt user for port number.
        if (port == -1) {
            System.out.print("Server port [" + defaultPort() + "]: ");
            String portCheck = scanner.nextLine().trim();
            if (!portCheck.isEmpty()) {
                port = Integer.parseInt(portCheck);
            } else {
                port = defaultPort();
            }
        }

        // If no token is given, prompt user for a token.
        if (token == null) {
            System.out.print("Access token (or Enter for none): ");
            String tokenCheck = scanner.nextLine().trim();
            token = tokenCheck.isEmpty() ? null : tokenCheck;
        }

        return new ClientConfig(host, port, null, token);
    }

    /**
     * Validates the TUI configuration.
     * This method checks if the host, port, user, and token values are valid.
     * @param host The host name of the chat server.
     * @param port The port number of the chat server.
     * @param user The user name of the client.
     * @param token The access token for authentication.
     *             This is used to verify the identity of the client connecting to the server.
     * @return A new ClientConfig object with the provided values.
     *         The object contains the host name, port number, user name, and access token.
     */
    public static ClientConfig validateCreate(String host,
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
        return new ClientConfig(host, port, user, token);
    }

    /**
     * Returns the default port number for the chat server.
     * This method is called to retrieve the default port number used by the chat client.
     *
     * @return The default port number (5555).
     */
    public static int defaultPort() {
        return DEFAULT_PORT;
    }
}
