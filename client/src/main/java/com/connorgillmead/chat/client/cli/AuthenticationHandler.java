// AuthenticationHandler.java

package com.connorgillmead.chat.client.cli;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

/**
 * AuthenticationHandler is responsible for managing user authentication in the
 * chat client.
 * It provides methods to log in, register, and handle authentication attempts.
 * The class uses a Scanner for user input and a PrintWriter for sending
 * messages to the server.
 */
public class AuthenticationHandler {
    private static final int MAX_AUTH_ATTEMPTS = 5;
    private final Scanner scanner;
    private final PrintWriter output;
    private final BufferedReader input;
    private String authenticatedUsername;
    private String token;

    /**
     * Constructor for AuthenticationHandler.
     * Initializes the handler with a Scanner for user input, a PrintWriter for
     * output,
     * and a BufferedReader for reading server responses.
     *
     * @param scanner The Scanner object to read user input from the console.
     * @param output  The PrintWriter object to send messages to the server.
     * @param input   The BufferedReader object to read responses from the server.
     */
    public AuthenticationHandler(Scanner scanner, PrintWriter output, BufferedReader input, String token) {
        this.scanner = scanner;
        this.output = output;
        this.input = input;
        this.token = token;
    }

    /**
     * Returns the token used for authentication.
     * This method is called after a successful authentication to retrieve the token.
     *
     * @return The token used for authentication, or null if not set.
     */
    public String getToken() {
        return token;
    }

    /**
     * Authenticates the user by allowing them to log in or register.
     * The method prompts the user for their credentials and sends an authentication
     * request to the server.
     * It allows a maximum number of authentication attempts before giving up.
     *
     * @return true if authentication is successful, false if the user chooses to
     *         exit or maximum attempts are reached.
     * @throws IOException If an I/O error occurs while reading from or writing to
     *                     the server.
     */
    public boolean authenticate() throws IOException {
        int attempts = 0;
        boolean authenticated = false;

        while (attempts <= MAX_AUTH_ATTEMPTS && !authenticated) {
            System.out.println("Choose action: [1] Login, [2] Register, [3] Exit");
            String choice = scanner.nextLine().trim();
            String username;
            String password;
            ChatMessage authorisationRequest = null;
            String tentativeUsername = null;

            switch (choice) {
                case "1":
                    System.out.print("Enter username: ");
                    username = scanner.nextLine().trim();
                    System.out.print("Enter password: ");
                    password = scanner.nextLine().trim();
                    authorisationRequest = ChatMessage.login(username, password);
                    if (token != null && !token.isBlank()) {
                        authorisationRequest.setToken(token);
                    }
                    tentativeUsername = username;
                    break;

                case "2":
                    System.out.print("Enter new username: ");
                    username = scanner.nextLine().trim();
                    System.out.print("Enter new password: ");
                    password = scanner.nextLine().trim();
                    authorisationRequest = ChatMessage.register(username, password);
                    if (token != null && !token.isBlank()) {
                        authorisationRequest.setToken(token);
                    }
                    tentativeUsername = username;
                    break;

                case "3": // Exit
                    System.out.println("Exiting authentication process.");
                    return false;

                default:
                    System.out.println("Invalid choice. Please try again.");
                    attempts++;
                    continue;
            }

            output.println(authorisationRequest.toJson());
            output.flush();
            String responseLine = input.readLine();
            if (responseLine == null) {
                System.out.println("Server connection lost. Exiting.");
                return false;
            }
            ChatMessage response = ChatMessage.fromJson(responseLine);

            System.out.println("Server: " + response.getMessage());

            if (!response.isSuccess() && isTokenEror(response.getMessage())) {
                System.out.println("Invalid token. Please re-enter your token.");
                promptForToken();
                continue;
            }

            if (response.isSuccess()) {
                switch (response.getType()) {
                    case "login_response" -> {
                        authenticated = true;
                        authenticatedUsername = tentativeUsername;
                    }

                    case "register_response" -> {
                        authenticated = true;
                        authenticatedUsername = tentativeUsername;
                    }

                    default -> {
                        System.out.println("Unexpected response type: " + response.getType());
                    }
                }
            } else {
                System.out.println("Authentication failed: " + (MAX_AUTH_ATTEMPTS - attempts) + " attempts remaining.");
                attempts++;
            }
        }
        if (!authenticated) {
            System.out.println("Maxium authentication attempts reached or chose to exit. Exiting.");
            return false;
        }
        return true;
    }

    /**
     * Returns the username of the authenticated user.
     * This method is called after a successful authentication to retrieve the
     * username.
     *
     * @return The username of the authenticated user, or null if not authenticated.
     */
    public String getAuthenticatedUsername() {
        return authenticatedUsername;
    }

    private static boolean isTokenEror(String message) {
        return message != null && message.toLowerCase().contains("token");
    }

    private void promptForToken() {
        System.out.println("Please enter your token:");
        String enteredToken = scanner.nextLine().trim();
        while (enteredToken.isBlank()) {
            System.out.println("Token cannot be empty. Please enter your token:");
            enteredToken = scanner.nextLine().trim();
        }
        this.token = enteredToken;
    }
}
