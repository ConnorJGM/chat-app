package com.connorgillmead.chat.client.utilities;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Authentication is a utility class that provides methods for handling user authentication
 * in a chat application. It allows users to log in or register and manages the authentication
 * process with the server.
 */
public final class Authentication {
    /**
     * The maximum number of authentication attempts allowed.
     * This is used to prevent infinite loops in case of repeated failures.
     */
    public static final int MAX_AUTH_ATTEMPTS = 5;

    /**
     * Private constructor to prevent instantiation.
     */
    private Authentication() {
    }

    /**
     * Sends an authentication request (login or register) to the server.
     *
     * @param actionType     The type of action ("login" or "register").
     * @param username       The username.
     * @param password       The password.
     * @param token          The current session token (can be null).
     * @param printWriter    The PrintWriter to send the request.
     * @param bufferedReader The BufferedReader to read the server's response.
     * @return The ChatMessage response from the server.
     * @throws IOException If an I/O error occurs or the connection is lost.
     */
    public static ChatMessage sendAuthenticationRequest(String actionType, String username, String password,
                String token, PrintWriter printWriter, BufferedReader bufferedReader) throws IOException {
        ChatMessage authenticationRequest;
        if ("login".equalsIgnoreCase(actionType)) {
            authenticationRequest = ChatMessage.login(username, password);
        } else if ("register".equalsIgnoreCase(actionType)) {
            authenticationRequest = ChatMessage.register(username, password);
        } else {
            throw new IllegalArgumentException(
                    "Invalid action type: " + actionType + ". Must be 'login' or 'register'.");
        }

        if (token != null && !token.isBlank()) {
            authenticationRequest.setToken(token);
        }

        printWriter.println(authenticationRequest.toJson());
        printWriter.flush();

        String responseLine = bufferedReader.readLine();
        if (responseLine == null) {
            // Or return a specific error ChatMessage if preferred
            throw new IOException("Server connection lost during authentication.");
        }
        return ChatMessage.fromJson(responseLine);
    }

    /**
     * Checks if an error message indicates a token-related issue.
     *
     * @param message The error message to check.
     * @return true if the message likely indicates a token error, false otherwise.
     */
    public static boolean tokenError(String message) {
        return message != null && message.toLowerCase().contains("token");
    }
}
