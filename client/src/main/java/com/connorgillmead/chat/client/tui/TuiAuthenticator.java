// TuiAuthenticator.java

package com.connorgillmead.chat.client.tui;

import com.connorgillmead.chat.common.ChatMessage;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * TuiAuthenticator is a utility class that provides methods for authenticating
 * users
 * in a terminal user interface (TUI) environment. It allows users to log in or
 * register
 * by presenting a series of prompts and handling user input through a graphical
 * interface.
 * The class uses Lanterna library components to create windows, buttons, and
 * text boxes
 * for user interaction.
 * It supports both login and registration processes, handles user input
 * validation,
 * and manages authentication attempts.
 * It also provides feedback to the user through dialog boxes
 * and messages based on the success or failure of the authentication process.
 * The authentication process can be customized with a maximum number of
 * attempts,
 * allowing the user to retry authentication a specified number of times before
 * giving up.
 */
final class TuiAuthenticator {

    // Private constructor to prevent instantiation.
    private TuiAuthenticator() {
    }

    /**
     * Authenticates a user through a terminal user interface.
     * This method presents the user with options to log in, register, or exit the
     * application.
     * It handles user input, validates credentials, and communicates with the
     * server.
     *
     * @param gui               The MultiWindowTextGUI instance for displaying
     *                          windows and dialogs.
     * @param screen            The Screen instance for rendering the TUI.
     * @param output            The PrintWriter for sending messages to the server.
     * @param input             The BufferedReader for reading responses from the
     *                          server.
     * @param authenticatedUser An AtomicReference to store the authenticated
     *                          username.
     * @param maxAttempts       The maximum number of authentication attempts
     *                          allowed.
     * @return true if authentication is successful, false otherwise.
     * @throws IOException If an I/O error occurs during communication with the
     *                     server.
     */
    public static boolean authenticate(MultiWindowTextGUI gui, Screen screen, PrintWriter output,
            BufferedReader input, AtomicReference<String> authenticatedUser,
            AtomicReference<String> tokenReference, int maxAttempts) throws IOException {
        int attempts = 0;
        boolean authenticated = false;
        final int column = 25;

        String currentToken = tokenReference.get();

        // The loop continues until the user is authenticated or the maximum number of
        // attempts is reached.
        while (attempts <= maxAttempts && !authenticated) {
            AtomicReference<String> user = new AtomicReference<>();
            BasicWindow window = new BasicWindow("Authentication");
            Panel panel = new Panel(new GridLayout(1));
            panel.addComponent(new Button("Login", () -> {
                user.set("login");
                window.close();
            }));
            panel.addComponent(new Button("Register", () -> {
                user.set("register");
                window.close();
            }));
            panel.addComponent(new Button("Exit Application", () -> {
                user.set("exit_application");
                window.close();
            }));
            window.setComponent(panel);

            window.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onUnhandledInput(Window window, KeyStroke keyStroke, AtomicBoolean handled) {
                    if (keyStroke.getKeyType() == KeyType.Escape) {
                        handled.set(true);
                        user.set("exit_step");
                        window.close();
                    }
                }
            });
            gui.addWindowAndWait(window);
            String action = user.get();
            if (action == null || "exit_application".equals(action) || "exit_step".equals(action)) {
                return false;
            }

            // Prepare for user input for login or registration.
            String tentativeUsername;
            ChatMessage authenticationRequest;
            BasicWindow credentialWindow = new BasicWindow("login".equals(action) ? "Login" : "Register");
            Panel credentialPanel = new Panel(new GridLayout(2));
            TextBox usernameBox = new TextBox().setPreferredSize(new TerminalSize(column, 1));
            TextBox passwordBox = new TextBox().setMask('*').setPreferredSize(new TerminalSize(column, 1));
            credentialPanel.addComponent(new Label("Username: "));
            credentialPanel.addComponent(usernameBox);
            credentialPanel.addComponent(new Label("Password: "));
            credentialPanel.addComponent(passwordBox);

            AtomicBoolean submitted = new AtomicBoolean(false);
            Button submitButton = new Button("Submit", () -> {
                if (usernameBox.getText().trim().isEmpty() || passwordBox.getText().trim().isEmpty()) {
                    new MessageDialogBuilder().setTitle("Error")
                            .setText("Username and password cannot be empty.")
                            .addButton(MessageDialogButton.OK).build().showDialog(gui);
                } else {
                    submitted.set(true);
                    credentialWindow.close();
                }
            });
            credentialPanel.addComponent(new EmptySpace(new TerminalSize(0, 0)));
            credentialPanel.addComponent(submitButton);
            credentialWindow.setComponent(credentialPanel);
            credentialWindow.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onUnhandledInput(Window window, KeyStroke keyStroke, AtomicBoolean handled) {
                    if (keyStroke.getKeyType() == KeyType.Escape) {
                        handled.set(true);
                        submitted.set(false);
                        window.close();
                    }
                }
            });
            gui.addWindowAndWait(credentialWindow);

            if (!submitted.get()) {
                attempts++;
                if (attempts >= maxAttempts && !authenticated) {
                    new MessageDialogBuilder().setTitle("Authentication Failed")
                            .setText("Maximum attempts reached. Exiting.")
                            .addButton(MessageDialogButton.OK).build().showDialog(gui);
                    return false;
                }
                continue;
            }

            // This section reads the input from the text boxes and prepares the
            // authentication request.
            String username = usernameBox.getText().trim();
            String password = passwordBox.getText().trim();
            tentativeUsername = username;
            authenticationRequest = "login".equals(action)
                    ? ChatMessage.login(username, password)
                    : ChatMessage.register(username, password);
            authenticationRequest.setToken(currentToken);

            output.println(authenticationRequest.toJson());
            output.flush();

            ChatMessage responseMessage = ChatMessage.fromJson(input.readLine());

            if (responseMessage.isSuccess()) {
                authenticatedUser.set(tentativeUsername);
                tokenReference.set(currentToken);
                showSuccess(gui, responseMessage.getMessage());
                return true;
            }

            if (isTokenError(responseMessage.getMessage())) {
                new MessageDialogBuilder()
                        .setTitle("Token Error")
                        .setText(responseMessage.getMessage())
                        .addButton(MessageDialogButton.OK)
                        .build()
                        .showDialog(gui);
                currentToken = promptToken(gui);
                if (currentToken == null) {
                    return false;
                }
                tokenReference.set(currentToken);
                continue;
            }

            // General authentication failure
            attempts++;
            int remainingAttempts = maxAttempts - attempts;
            authorisationFail(gui, responseMessage.getMessage(), remainingAttempts);
        }
        return false;
    }

    /**
     * Checks if the error message indicates a token-related issue.
     * This method checks if the provided message contains the word "token" in a
     * case-insensitive manner.
     *
     * @param message The error message to check.
     * @return true if the message contains "token", false otherwise.
     */
    private static boolean isTokenError(String message) {
        return message != null && message.toLowerCase().contains("token");
    }

    /**
     * Prompts the user for a token through a dialog.
     * This method displays a dialog asking the user to enter a valid token.
     * It validates the input to ensure that the token is not empty.
     *
     * @param gui The MultiWindowTextGUI instance for displaying the dialog.
     * @return The trimmed token entered by the user, or null if the dialog is
     *         cancelled.
     */
    private static String promptToken(MultiWindowTextGUI gui) {
        String token = new TextInputDialogBuilder().setTitle("Token Required")
            .setDescription("Please enter a valid token:")
            .setValidationPattern(Pattern.compile(".*\\S.*"), "Token cannot be empty.")
            .build().showDialog(gui);
        if (token == null) {
            return null;
        }

        while (token.isBlank()) {
            token = new TextInputDialogBuilder().setTitle("Invalid Token")
                .setDescription("Token cannot be empty. Please enter a valid token:")
                .setValidationPattern(Pattern.compile(".*\\S.*"), "Token cannot be empty.")
                .build().showDialog(gui);
            if (token == null) {
                return null;
            }
        }
        return token.trim();
    }

    /**
     * Displays an authorisation failure message dialog to the user.
     *
     * @param gui               The MultiWindowTextGUI instance for displaying the dialog.
     * @param message           The message to display in the dialog.
     * @param remainingAttempts The number of remaining authentication attempts.
     */
    private static void authorisationFail(MultiWindowTextGUI gui, String message, int remainingAttempts) {
        String attemptsMessage = message.isEmpty()
                ? "Authentication failed. Please try again."
                : message;
        if (remainingAttempts > 0) {
            attemptsMessage += " " + remainingAttempts + " attempts remaining.";
        } else {
            attemptsMessage += " No attempts remaining.";
        }
        new MessageDialogBuilder()
                .setTitle("Authentication Failed")
                .setText(attemptsMessage)
                .addButton(MessageDialogButton.OK)
                .build()
                .showDialog(gui);
    }

    /**
     * Displays a success message dialog to the user after successful authentication.
     *
     * @param gui      The MultiWindowTextGUI instance for displaying the dialog.
     * @param username The username of the authenticated user.
     */
    private static void showSuccess(MultiWindowTextGUI gui, String username) {
        new MessageDialogBuilder()
                .setTitle("Success")
                .setText("Authentication successful. Welcome, " + username + "!")
                .addButton(MessageDialogButton.OK)
                .build()
                .showDialog(gui);
    }
}
