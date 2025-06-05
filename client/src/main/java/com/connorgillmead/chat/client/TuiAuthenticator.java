package com.connorgillmead.chat.client;

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
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TuiAuthenticator is a utility class that provides methods for authenticating users
 * in a terminal user interface (TUI) environment. It allows users to log in or register
 * by presenting a series of prompts and handling user input through a graphical interface.
 * The class uses Lanterna library components to create windows, buttons, and text boxes
 * for user interaction.
 * It supports both login and registration processes, handles user input validation,
 * and manages authentication attempts.
 * It also provides feedback to the user through dialog boxes
 * and messages based on the success or failure of the authentication process.
 * The authentication process can be customized with a maximum number of attempts,
 * allowing the user to retry authentication a specified number of times before giving up.
 */
final class TuiAuthenticator {

    // Private constructor to prevent instantiation.
    private TuiAuthenticator() { }

    /**
     * Authenticates a user through a terminal user interface.
     * This method presents the user with options to log in, register, or exit the application.
     * It handles user input, validates credentials, and communicates with the server.
     *
     * @param gui The MultiWindowTextGUI instance for displaying windows and dialogs.
     * @param screen The Screen instance for rendering the TUI.
     * @param output The PrintWriter for sending messages to the server.
     * @param input The BufferedReader for reading responses from the server.
     * @param authenticatedUser An AtomicReference to store the authenticated username.
     * @param maxAttempts The maximum number of authentication attempts allowed.
     * @return true if authentication is successful, false otherwise.
     * @throws IOException If an I/O error occurs during communication with the server.
     */
    public static boolean authenticate(MultiWindowTextGUI gui, Screen screen, PrintWriter output, BufferedReader input,
                                       AtomicReference<String> authenticatedUser, int maxAttempts) throws IOException {
        int attempts = 0;
        boolean authenticated = false;
        final int column = 25;

        // While loop to allow multiple authentication attempts.
        // The loop continues until the user is authenticated or the maximum number of attempts is reached.
        while (attempts < maxAttempts && !authenticated) {
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
            // This section creates a new window with text boxes for username and password input.
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

            // Retrieve the username and password from the text boxes.
            // This section reads the input from the text boxes and prepares the authentication request.
            String username = usernameBox.getText().trim();
            String password = passwordBox.getText().trim();
            tentativeUsername = username;
            authenticationRequest = "login".equals(action)
                ? ChatMessage.login(username, password)
                : ChatMessage.register(username, password);

            output.println(authenticationRequest.toJson());
            output.flush();
            String responseLine = input.readLine();
            if (responseLine == null) {
                new MessageDialogBuilder().setTitle("Error")
                    .setText("Connection to server lost.")
                    .addButton(MessageDialogButton.OK).build().showDialog(gui);
                return false;
            }
            ChatMessage response = ChatMessage.fromJson(responseLine);
            if (response.isSuccess() && (response.getType().equals("login_response")
                || response.getType().equals("register_response"))) {
                authenticated = true;
                authenticatedUser.set(tentativeUsername);
                new MessageDialogBuilder().setTitle("Success")
                    .setText("Authentication successful. Welcome, " + tentativeUsername + "!")
                    .addButton(MessageDialogButton.OK).build().showDialog(gui);
            } else {
                attempts++;
                int remainingAttempts = maxAttempts - attempts;
                String errorMessage = response.getMessage() != null
                    ? response.getMessage()
                    : "Authentication failed. Please try again.";
                String attemptsMessage = errorMessage
                    + (remainingAttempts > 0
                       ? " " + remainingAttempts + " attempts remaining."
                       : " No attempts remaining.");
                new MessageDialogBuilder().setTitle("Authentication Failed")
                    .setText(attemptsMessage)
                    .addButton(MessageDialogButton.OK).build().showDialog(gui);

                if (attempts >= maxAttempts && !authenticated) {
                    new MessageDialogBuilder().setTitle("Authentication Failed")
                        .setText("Maximum attempts reached. Exiting.")
                        .addButton(MessageDialogButton.OK).build().showDialog(gui);
                    return false;
                }
            }
        }
        return authenticated;
    }
}
