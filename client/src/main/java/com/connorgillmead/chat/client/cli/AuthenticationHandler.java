// AuthenticationHandler.java

package com.connorgillmead.chat.client.cli;

import com.connorgillmead.chat.client.utilities.Authentication;
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
    private final Scanner scanner;
    private final PrintWriter printWriter;
    private final BufferedReader bufferedReader;
    private String authenticatedUsername;
    private String token;

    /**
     * Constructor for AuthenticationHandler.
     * Initializes the handler with a Scanner for user input, a PrintWriter for
     * output,
     * and a BufferedReader for reading server responses.
     *
     * @param scanner The Scanner object to read user input from the console.
     * @param printWriter  The PrintWriter object to send messages to the server.
     * @param bufferedReader   The BufferedReader object to read responses from the server.
     */
    public AuthenticationHandler(Scanner scanner, PrintWriter printWriter,
                                 BufferedReader bufferedReader, String token) {
        this.scanner = scanner;
        this.printWriter = printWriter;
        this.bufferedReader = bufferedReader;
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

        while (attempts <= Authentication.MAX_AUTH_ATTEMPTS && !authenticated) {
            System.out.println("Choose action: [1] Login, [2] Register, [3] Exit");
            String choice = scanner.nextLine().trim();
            String username;
            String password;
            String tentativeUsername;
            String actionType;

            switch (choice) {
                case "1":
                    System.out.print("Enter username: ");
                    username = scanner.nextLine().trim();
                    System.out.print("Enter password: ");
                    password = scanner.nextLine().trim();
                    actionType = "login";
                    tentativeUsername = username;
                    break;

                case "2":
                    System.out.print("Enter new username: ");
                    username = scanner.nextLine().trim();
                    System.out.print("Enter new password: ");
                    password = scanner.nextLine().trim();
                    actionType = "register";
                    tentativeUsername = username;
                    break;

                case "3":
                    System.out.println("Exiting authentication process.");
                    return false;

                default:
                    System.out.println("Invalid choice. Please try again.");
                    attempts++;
                    continue;
            }

            ChatMessage response;
            try {
                response = Authentication.sendAuthenticationRequest(actionType, username, password,
                                                                    this.token, printWriter, bufferedReader);
            } catch (IOException e) {
                System.err.println("Error during authentication: " + e.getMessage());
                return false;
            }

            System.out.println("Server: " + response.getMessage());

            if (!response.isSuccess() && Authentication.tokenError(response.getMessage())) {
                System.out.println("Invalid token. Please re-enter your token.");
                promptForToken();
                continue;
            }

            if (response.isSuccess()) {
                if ("login_response".equals(response.getType())
                        || "register_response".equals(response.getType())) {
                    authenticated = true;
                    authenticatedUsername = tentativeUsername;
                } else {
                    System.out.println("Unexpected response type: " + response.getType());
                }
            } else {
                System.out.println("Authentication failed: "
                    + (Authentication.MAX_AUTH_ATTEMPTS - attempts) + " attempts remaining.");
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
