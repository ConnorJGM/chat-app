// TuiAuthenticator.java

package com.connorgillmead.chat.client.tui;

import com.connorgillmead.chat.client.utilities.Authentication;
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
     * Authenticates the user by allowing them to log in or register.
     * This method presents a series of windows for the user to choose an action
     * (login or register),
     * input their credentials, and handle authentication attempts.
     *
     * @param gui               The MultiWindowTextGUI instance for displaying the
     *                          windows.
     * @param screen            The Screen instance for rendering the GUI.
     * @param printWriter       The PrintWriter for sending messages to the server.
     * @param bufferedReader    The BufferedReader for reading responses from the
     *                          server.
     * @param authenticatedUser An AtomicReference to hold the authenticated user's
     *                          username.
     * @param tokenReference    An AtomicReference to hold the authentication token.
     * @param maxAttempts       The maximum number of authentication attempts allowed.
     * @return true if authentication is successful, false otherwise.
     * @throws IOException If an I/O error occurs during authentication.
     */
    public static boolean authenticate(MultiWindowTextGUI gui, Screen screen, PrintWriter printWriter,
            BufferedReader bufferedReader, AtomicReference<String> authenticatedUser,
            AtomicReference<String> tokenReference, int maxAttempts) throws IOException {
        int attempts = 0;
        boolean authenticated = false;

        String currentToken = tokenReference.get();

        while (attempts <= maxAttempts && !authenticated) {
            String action = authenticationWindow(gui);

            if (action == null || "exit_application".equals(action) || "exit_step".equals(action)) {
                return false;
            }

            Credentials credential = showCredentialWindow(gui, action);

            if (!credential.submitted()) {
                attempts++;
                if (attempts >= maxAttempts && !authenticated) {
                    new MessageDialogBuilder().setTitle("Authentication Failed")
                            .setText("Maximum attempts reached. Exiting.")
                            .addButton(MessageDialogButton.OK).build().showDialog(gui);
                    return false;
                }
                continue;
            }

            String username = credential.username();
            String password = credential.password();
            String tentativeUsername = username;

            ChatMessage responseMessage;
            try {
                responseMessage = Authentication.sendAuthenticationRequest(action, username, password,
                                                                           currentToken, printWriter, bufferedReader);
            } catch (IOException error) {
                new MessageDialogBuilder().setTitle("Error")
                        .setText("An error occurred during authentication: " + error.getMessage())
                        .addButton(MessageDialogButton.OK).build().showDialog(gui);
                return false;
            }

            if (responseMessage.isSuccess()) {
                authenticatedUser.set(tentativeUsername);

                if (responseMessage.getToken() != null && !responseMessage.getToken().isEmpty()) {
                    tokenReference.set(responseMessage.getToken());
                }
                showSuccess(gui, responseMessage.getMessage());
                return true;
            }

            if (Authentication.tokenError(responseMessage.getMessage())) {
                new MessageDialogBuilder().setTitle("Token Error")
                        .setText(responseMessage.getMessage())
                        .addButton(MessageDialogButton.OK)
                        .build().showDialog(gui);
                currentToken = promptToken(gui);
                if (currentToken == null) {
                    return false;
                }
                tokenReference.set(currentToken);
                continue;
            }

            attempts++;
            int remainingAttempts = maxAttempts - attempts;
            authorisationFail(gui, responseMessage.getMessage(), remainingAttempts);
        }
        return false;
    }

    /**
     * Displays a window for the user to choose an authentication action (login,
     * register, or exit).
     * This method creates a window with buttons for each action and waits for the
     * user's choice.
     *
     * @param gui The MultiWindowTextGUI instance for displaying the window.
     * @return A string representing the user's action choice, or null if the user
     *         chooses to exit.
     */
    private static String authenticationWindow(MultiWindowTextGUI gui) {
        AtomicReference<String> userAction = new AtomicReference<>();
        BasicWindow window = new BasicWindow("Authentication");
        Panel panel = new Panel(new GridLayout(1));
        panel.addComponent(new Button("Login", () -> {
            userAction.set("login");
            window.close();
        }));
        panel.addComponent(new Button("Register", () -> {
            userAction.set("register");
            window.close();
        }));
        panel.addComponent(new Button("Exit Application", () -> {
            userAction.set("exit_application");
            window.close();
        }));
        window.setComponent(panel);
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window w, KeyStroke keyStroke, AtomicBoolean handled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    handled.set(true);
                    userAction.set("exit_step");
                    w.close();
                }
            }
        });
        gui.addWindowAndWait(window);
        return userAction.get();
    }

    /**
     * A record to hold the result of credential input.
     * This record contains the username, password, and a flag indicating whether
     * the input was submitted.
     *
     * @param username The username entered by the user.
     * @param password The password entered by the user.
     * @param submitted A boolean indicating whether the input was submitted
     *                (true) or cancelled (false).
     */
    private record Credentials(String username, String password, boolean submitted) { }

    /**
     * Displays a window for user credential input (username and password).
     * This method creates a window with text boxes for username and password,
     * and a submit button.
     * It validates the input to ensure that both fields are not empty.
     *
     * @param gui    The MultiWindowTextGUI instance for displaying the window.
     * @param action The action type, either "login" or "register".
     * @return A CredentialInputResult containing the entered username and password,
     *         or null if the dialog is cancelled.
     */
    private static Credentials showCredentialWindow(MultiWindowTextGUI gui, String action) {
        final int column = 25;
        BasicWindow credentialWindow = new BasicWindow("login".equals(action) ? "Login" : "Register");
        Panel credentialPanel = new Panel(new GridLayout(2));
        TextBox usernameBox = new TextBox().setPreferredSize(new TerminalSize(column, 1));
        TextBox passwordBox = new TextBox().setMask('*').setPreferredSize(new TerminalSize(column, 1));
        credentialPanel.addComponent(new Label("Username: "));
        credentialPanel.addComponent(usernameBox);
        credentialPanel.addComponent(new Label("Password: "));
        credentialPanel.addComponent(passwordBox);

        AtomicBoolean submittedFlag = new AtomicBoolean(false);
        Button submitButton = new Button("Submit", () -> {
            if (usernameBox.getText().trim().isEmpty() || passwordBox.getText().trim().isEmpty()) {
                new MessageDialogBuilder().setTitle("Error")
                        .setText("Username and password cannot be empty.")
                        .addButton(MessageDialogButton.OK).build().showDialog(gui);
            } else {
                submittedFlag.set(true);
                credentialWindow.close();
            }
        });
        credentialPanel.addComponent(new EmptySpace(new TerminalSize(0, 0)));
        credentialPanel.addComponent(submitButton);
        credentialWindow.setComponent(credentialPanel);
        credentialWindow.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window w, KeyStroke keyStroke, AtomicBoolean handled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    handled.set(true);
                    submittedFlag.set(false);
                    w.close();
                }
            }
        });
        gui.addWindowAndWait(credentialWindow);
        if (submittedFlag.get()) {
            return new Credentials(usernameBox.getText().trim(), passwordBox.getText().trim(), true);
        }
        return new Credentials(null, null, false);
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
                .setTitle("Authentication Failed").setText(attemptsMessage)
                .addButton(MessageDialogButton.OK).build().showDialog(gui);
    }

    /**
     * Displays a success message dialog to the user after successful authentication.
     *
     * @param gui      The MultiWindowTextGUI instance for displaying the dialog.
     * @param username The username of the authenticated user.
     */
    private static void showSuccess(MultiWindowTextGUI gui, String username) {
        new MessageDialogBuilder().setTitle("Success")
                .setText("Authentication successful. Welcome, " + username + "!")
                .addButton(MessageDialogButton.OK).build().showDialog(gui);
    }
}
