// ChatClientTuiApp.java

package com.connorgillmead.chat.client.tui;

import com.connorgillmead.chat.client.cli.ChatClient;
import com.connorgillmead.chat.client.cli.ChatClientNet;
import com.connorgillmead.chat.client.cli.ClientConfig;
import com.connorgillmead.chat.common.ChatMessage;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
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
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.swing.AWTTerminalFrame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChatClientTuiApp is a simple command-line chat client that connects to a chat server.
 * The client runs in two threads: one for sending messages and another for receiving messages.
 * It uses the Lanterna library to create a text-based user interface (TUI).
 * The client prompts the user for connection details (host, port, username, and token) and then connects to the server.
 */
public final class ChatClientTuiApp {

    // Variables for the terminal size and message drain interval.
    // These are used to set the size of the text box and the interval for draining messages from the queue.
    private static final int COLUMN_LOG = 100;
    private static final int COLUMN_LIST = 20;
    private static final int COLUMN = COLUMN_LOG + COLUMN_LIST + 4;
    private static final int ROW = 60;
    private static final int ROW_LIST = ROW + 4;
    private static final int DRAIN = 100;
    private static final int MAX_AUTH_ATTEMPTS = 5;

    private Screen screen;
    private MultiWindowTextGUI gui;
    private AWTTerminalFrame frame;
    private BasicWindow window;
    private ClientConfig config;
    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private AtomicReference<String> authenticatedUser = new AtomicReference<>();
    private BlockingQueue<ChatMessage> inbound = new LinkedBlockingQueue<>();
    private DrainContext context;
    private Timer timer;
    private int everyMillis;

    // Constructor for ChatClientTuiApp.
    // Initialises the timer and sets the drain interval.
    public ChatClientTuiApp() {
        this.timer = new Timer("drain", true);
        this.everyMillis = DRAIN;
    }

    /**
     * Main method to start the lanterna client.
     * It connects to the server and starts two threads: one for sending messages and another for receiving messages.
     * @param arguments The first argument is the hostname, and the second argument is the port number.
     * @throws IOException If an I/O error occurs when creating the socket or transferring data.
     * @throws GeneralSecurityException If a security error occurs when creating the socket.
     *         This can happen if the SSL/TLS protocol is not supported or if the trust manager cannot be initialised.
     */
    public static void main(String[] arguments) {
        try {
            ChatClientTuiApp tuiApp = new ChatClientTuiApp();
            tuiApp.run();
        } catch (IOException | GeneralSecurityException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * runLanternaClient(...) creates a new terminal screen using the DefaultTerminalFactory.
     * It prompts the user for connection details (host, port, username, and token) and then connects to the server.
     * It launches the chat GUI and starts two threads: one for sending messages and another for receiving messages.
     * @throws IOException If an I/O error occurs when creating the socket or transferring data.
     * @throws GeneralSecurityException If a security error occurs when creating the socket.
     */
    private void run() throws IOException, GeneralSecurityException {

        // Create a new terminal screen using the DefaultTerminalFactory.
        // This is used to create a text-based user interface (TUI).
        this.frame = new DefaultTerminalFactory()
                            .setTerminalEmulatorTitle("Chat Client")
                            .setInitialTerminalSize(new TerminalSize(COLUMN, ROW_LIST))
                            .createAWTTerminal();

        // Try to create the terminal screen and GUI.
        // If successful, it will prompt the user for connection details.
        try {
            this.frame.pack();
            this.frame.setVisible(true);
            this.frame.setLocationRelativeTo(null);
            this.screen = new TerminalScreen(this.frame);
            this.screen.startScreen();
            this.gui = new MultiWindowTextGUI(this.screen);

            // Finalise the screen and GUI.
            final Screen finalScreen = this.screen;
            final MultiWindowTextGUI finalGui = this.gui;

            // Resize the window when the terminal is resized.
            this.frame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent error) {
                    finalGui.getGUIThread().invokeLater(() -> {
                        finalScreen.doResizeIfNecessary();
                    });
                }
            });

            // Create a holder for the config.
            AtomicReference<ClientConfig> configHolder = new AtomicReference<>();

            // This window prompts the user for connection details (host, port, username, and token).
            final BasicWindow configWindow = new BasicWindow("Connect to Chat Server");
            Panel form = new Panel(new GridLayout(2));
            TextBox hostBox  = new TextBox().setText("localhost");
            TextBox portBox  = new TextBox().setText(String.valueOf(ClientConfig.defaultPort()));
            TextBox tokenBox = new TextBox();
            form.addComponent(new Label("Host:"));
            form.addComponent(hostBox);
            form.addComponent(new Label("Port:"));
            form.addComponent(portBox);
            form.addComponent(new Label("Token (optional):"));
            form.addComponent(tokenBox);

            // This button is used to connect to the chat server.
            Button connect = new Button("Connect", () -> {
                try {
                    String host  = hostBox .getText().trim();
                    int    port  = Integer.parseInt(portBox.getText().trim());
                    String token = tokenBox.getText().trim().isEmpty()
                                ? null
                                : tokenBox.getText().trim();

                    // This is used to store the configuration details entered by the user.
                    ClientConfig temporaryConfig = ClientConfig.validateCreate(host, port, "temporaryUser", token);
                    configHolder.set(temporaryConfig);
                    configWindow.close();

                // If the user does not enter a valid host, port, or username,
                // show an error message dialog.
                } catch (NumberFormatException exception) {
                    new MessageDialogBuilder().setTitle("Invalid Port")
                            .setText("Port must be a number between 1 and 65535.")
                            .addButton(MessageDialogButton.OK).build().showDialog(finalGui);
                } catch (IllegalArgumentException error) {
                    new MessageDialogBuilder()
                            .setTitle("Invalid Input").setText(error.getMessage())
                            .addButton(MessageDialogButton.OK).build().showDialog(finalGui);
                }
            });

            // Press ESC to quit.
            configWindow.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onUnhandledInput(Window eventWindow, KeyStroke keyStroke, AtomicBoolean handled) {
                    if (keyStroke.getKeyType() == KeyType.Escape) {
                        handled.set(true);
                        configHolder.set(null);
                        eventWindow.close();
                    }
                }
            });

            // Add empty space and the connect button to the form.
            form.addComponent(
                new EmptySpace(new TerminalSize(0, 1)),
                    GridLayout.createHorizontallyFilledLayoutData(2)
            );
            form.addComponent(
                connect,
                GridLayout.createHorizontallyEndAlignedLayoutData(2)
            );
            configWindow.setComponent(form);
            finalGui.addWindowAndWait(configWindow);

            // This is the configuration details entered by the user.
            ClientConfig intialConfig = configHolder.get();
            this.config = intialConfig;

            try (ChatClient chatClient = new ChatClient(this.config.host(), this.config.port())) {
                this.socket = chatClient.socket();
                this.printWriter = new PrintWriter(
                    this.socket.getOutputStream(), true, StandardCharsets.UTF_8);
                this.bufferedReader = new BufferedReader(
                    new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));

                AtomicReference<String> localAuthentication = new AtomicReference<>();
                AtomicReference<String> tokenReference = new AtomicReference<>(this.config.token());

                boolean autheticationSuccess = TuiAuthenticator.authenticate(
                    finalGui, finalScreen, this.printWriter, this.bufferedReader,
                    localAuthentication, tokenReference, MAX_AUTH_ATTEMPTS);

                if (autheticationSuccess && localAuthentication.get() != null) {
                    this.authenticatedUser.set(localAuthentication.get());

                    this.config = ClientConfig.validateCreate(this.config.host(), this.config.port(),
                                                  this.authenticatedUser.get(), tokenReference.get());

                    launchChatGui();

                } else {
                    showErrorDialog(finalGui, "Connection Error", "Failed to authenticate. "
                                    + "Please check your credentials and try again.");
                    this.socket.close();
                }
            } catch (IOException error) {
                showErrorDialog(finalGui, "Connection Error", "Failed to connect to the server: "
                                + error.getMessage());
                this.socket.close();
            }
        } finally {
            stopScreen(this.screen);
            if (this.frame != null) {
                this.frame.dispose();
            }
            if (this.timer != null) {
                this.timer.cancel();
            }
        }
    }

    /**
     * launchChatGui(...) creates the chat GUI and starts the client.
     * It connects to the server and starts two threads: one for sending messages and another for receiving messages.
     * It uses the Lanterna library to create a text-based user interface (TUI).
     * @param gui The GUI to update.
     * @param screen The screen to use.
     * @param config The configuration object containing host, port, user, and token information.
     */
    private void launchChatGui() {
        try {
            // Thread B – receive messages.
            // This thread reads messages from the server and puts them into a blocking queue.
            new Thread(() -> ChatClientNet.readLoop(this.socket, this.inbound)).start();

            // Create a new BasicWindow object to display the chat interface.
            // This window is used to display the chat messages and user input.
            this.window = new BasicWindow("Chat - " + this.config.host() + " - User: " + this.config.user());

            // New panel for the chat interface.
            // This panel is used to arrange the components in the window.
            Panel root = new Panel(new BorderLayout());
            Panel side = new Panel(new GridLayout(1));
            side.setPreferredSize(new TerminalSize(COLUMN_LIST, ROW));

            // Create a user list table.
            // This table is used to display the list of connected users.
            Table<String> userList = new Table<>("Users");
            userList.setCellSelection(false);
            side.addComponent(userList);

            // Add empty space to the side panel.
            // This space is used to separate the components in the panel.
            side.addComponent(
                new EmptySpace(new TerminalSize(0, 1)),
                GridLayout.createLayoutData(
                    GridLayout.Alignment.BEGINNING,
                    GridLayout.Alignment.END,
                    true,
                    false,
                    1,
                    1)
            );
            root.addComponent(side, BorderLayout.Location.RIGHT);

            // Handles input from the user.
            // This text box is used to get user input.
            TextBox userInput = new TextBox() {
                @Override public Result handleKeyStroke(KeyStroke keyStroke) {
                    if (keyStroke.getKeyType() == KeyType.Enter) {
                        String message = getText().trim();
                        if ("quit".equalsIgnoreCase(message)) {
                            ChatClientTuiApp.this.gui.getGUIThread().invokeLater(() -> {
                                askQuit(this);
                            });
                        } else if (!message.isEmpty()) {
                            printWriter.println(ChatMessage.of(ChatClientTuiApp.this.config.user(),
                                                                                message).toJson());
                        }
                        setText("");
                        return Result.HANDLED;
                    }
                    return super.handleKeyStroke(keyStroke);
                }
            }.setPreferredSize(new TerminalSize(COLUMN, 1));
            root.addComponent(userInput, BorderLayout.Location.BOTTOM);

            // Create a new TextBox object to display the chat messages.
            // This text box is used to display the chat messages in a multi-line format.
            // It is set to read-only mode to prevent user input.
            TextBox log = new TextBox(new TerminalSize(COLUMN_LOG, ROW),
                                     TextBox.Style.MULTI_LINE) {
                @Override
                public Result handleKeyStroke(KeyStroke keyStroke) {
                    switch (keyStroke.getKeyType()) {
                        case Tab:
                            gui.getGUIThread().invokeLater(userInput::takeFocus);
                            return Result.HANDLED;
                        case ArrowUp:
                        case ArrowDown:
                        case ArrowLeft:
                        case ArrowRight:
                        case PageUp:
                        case PageDown:
                        case Home:
                        case End:
                            return super.handleKeyStroke(keyStroke);
                        default:
                            // Ignore all other keys.
                            return Result.UNHANDLED;
                    }
                }
            };
            root.addComponent(log, BorderLayout.Location.CENTER);

            // Press ESC to quit.
            // This is done to allow the user to quit the application using the ESC key.
            window.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onUnhandledInput(Window eventWindow, KeyStroke keyStroke, AtomicBoolean handled) {
                    if (keyStroke.getKeyType() == KeyType.Escape) {
                        handled.set(true);
                        askQuit(userInput);
                    }
                }
            });
            this.window.setComponent(root);

            // Set the size of the window.
            this.frame.pack();
            this.screen.doResizeIfNecessary();

            // Focus on the input box.
            // This is done to allow the user to start typing immediately.
            this.gui.getGUIThread().invokeLater(userInput::takeFocus);

            // Notify the server that the user has joined the chat.
            this.printWriter.println(ChatMessage.hello(config.user(), config.token()).toJson());

            // Create a new DrainContext object to hold the context for draining messages.
            // This context contains references to the GUI, window, input box, log, and user list.
            this.context = new DrainContext(this.gui, this.window, userInput,
                                            log, userList, this.socket, this.screen);

            // Schedule the message drain operation at regular intervals.
            // The drainOnce method is called to read messages from the server and display them in the chat interface.
            startDrainLoop();

            // Close the socket when the window is closed.
            this.gui.addWindowAndWait(this.window);
        } finally {
            if (this.timer != null) {
                this.timer.cancel();
            }
            if (this.socket != null && !this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    /**
     * showErrorDialog(...) displays an error dialog with the specified title and message.
     * It is used to show error messages to the user in a dialog box.
     * @param dialogGui The GUI to update.
     * @param title The title of the dialog.
     * @param message The message to display in the dialog.
     */
    private void showErrorDialog(MultiWindowTextGUI dialogGui, String title, String message) {
        MultiWindowTextGUI effectiveGui = (this.gui != null) ? this.gui : dialogGui;
        gui.getGUIThread().invokeLater(() -> {
            new MessageDialogBuilder().setTitle(title).setText(message)
                .addButton(MessageDialogButton.OK).build().showDialog(effectiveGui);
        });
    }

    /**
     * doQuit(...) closes the socket and stops the screen.
     * It is called when the user confirms they want to quit the application.
     * It closes the socket connection to the server and stops the screen.
     * This method is used to clean up resources and exit the application gracefully.
     * @param gui The GUI to update.
     *             This GUI is used to display the chat interface.
     * @param socket The socket to close.
     *              This socket is used to communicate with the chat server.
     * @param screen The screen to stop.
     *              This screen is used to display the chat interface.
     */
    private void doQuit() {
        try {
            if (this.timer != null) {
                this.timer.cancel();
            }
            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        this.gui.getGUIThread().invokeLater(() -> {
            Window activeWindow = this.gui.getActiveWindow();
            if (activeWindow != null) {
                activeWindow.close();
            }
        });
    }

    /**
     * askQuit(...) prompts the user to confirm quitting the application.
     * It displays a message dialog asking if the user is sure they want to quit.
     * If the user confirms, it calls doQuit(...) to close the application.
     * If the user cancels, it refocuses the input box.
     * @param gui The GUI to update.
     * @param textBox The input box to refocus.
     * @param socket The socket to close.
     * @param screen The screen to stop.
     *              This screen is used to display the chat interface.
     */
    private void askQuit(TextBox textBox) {
        MessageDialogButton answer = new MessageDialogBuilder()
            .setTitle("Quit")
            .setText("Are you sure you want to quit?")
            .addButton(MessageDialogButton.Yes)
            .addButton(MessageDialogButton.No)
            .build().showDialog(this.gui);

        if (answer == MessageDialogButton.Yes) {
            if (this.window != null) {
                this.window.close();
            }
            doQuit();
        } else if (textBox != null) {
            this.gui.getGUIThread().invokeLater(textBox::takeFocus);
        }
    }

    /**
     * stopScreen(...) stops the screen and releases any associated resources.
     * This method is called when the application is quitting to ensure that the screen is stopped properly.
     * It handles any IOException that may occur when stopping the screen.
     * @param screen The screen to stop.
     */
    private void stopScreen(Screen stopScreen) {
        if (stopScreen != null) {
            try {
                stopScreen.stopScreen();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * DrainContext is a record that holds the context for draining messages.
     * It contains references to the GUI, window, input box, log, and user list.
     * This context is used to update the GUI when messages are received.
     * It is passed to the drainOnce(...) method to process messages.
     * @param gui The GUI to update.
     * @param window The window to close when quitting.
     * @param textBox The input box to refocus.
     * @param log The text box to display chat messages.
     * @param userList The table to display the user list.
     * @param socket The socket to close when quitting.
     * @param screen The screen to stop when quitting.
     */
    private record DrainContext(
        MultiWindowTextGUI gui,
        BasicWindow window,
        TextBox textBox,
        TextBox log,
        Table<String> userList,
        Socket socket,
        Screen screen
    ) { }

    /**
     * drainOnce(...) reads a single message from the queue and updates the GUI.
     * It handles different message types: "text", "roster", and "error".
     * It updates the log and user list accordingly.
     * @param inbound The queue to read from.
     * @param context The context for draining messages.
     */
    private boolean drainOnce() {
        ChatMessage message = this.inbound.poll();
        if (message == null) {
            return false;
        }

        // Define a delay for error messages.
        final int delay = 5000;

        // If the server is shutting down, show a dialog and quit.
        if (ChatMessage.SERVER_SHUTDOWN.equals(message.getType())) {
            this.context.gui.getGUIThread().invokeLater(() -> {
                new MessageDialogBuilder().setTitle("Server Shutdown")
                    .setText("The server is shutting down. Exiting...")
                    .addButton(MessageDialogButton.OK).build().showDialog(this.context.gui);
                this.doQuit();
            });
            return true;
        }

        // If the user is kicked, show a dialog and quit.
        // This is done to notify the user that they have been kicked from the chat.
        if (message.isKick()) {
            this.context.gui.getGUIThread().invokeLater(() -> {
                new MessageDialogBuilder().setTitle("Kicked").setText(message.getBody())
                    .addButton(MessageDialogButton.OK).build().showDialog(this.context.gui);
                this.doQuit();
            });
            return true;
        }

        // Process the message based on its type.
        // The message type can be "text", "roster", or "error".
        // The "text" type is used for regular chat messages.
        // The "roster" type is used to update the user list.
        // The "error" type is used to display error messages.
        switch (message.getType()) {
            case "text"   -> {
                this.context.gui.getGUIThread().invokeLater(() -> {
                    this.context.log.addLine(message.getUser() + ": " + message.getBody());
                    this.context.log.setCaretPosition(this.context.log.getLineCount() - 1, 0); // scroll
                });
            }
            case "roster" -> {
                this.context.userList.getTableModel().clear();
                message.getUserList().forEach(this.context.userList.getTableModel()::addRow);
                this.context.userList.invalidate();
            }
            case "error"  -> {
                this.context.log.addLine("*** " + message.getBody() + " ***");

                // Exit after 5 seconds.
                // This is done to allow the user to read the error message before quitting.
                new Timer("quit-after-error", true)
                    .schedule(new TimerTask() {
                        @Override public void run() {
                            ChatClientTuiApp.this.doQuit();
                        }
                    }, delay);
            }
            default -> { }
        }
        return true;
    }

    /**
     * startDrainLoop(...) starts a loop that drains messages from the queue.
     * It uses a Timer to schedule the message drain operation at regular intervals.
     * @param context The context for draining messages.
     *            This context contains references to the GUI, window, input box, log, and user list.
     * @param timer The timer to use for scheduling.
     *             This timer is used to schedule tasks for future execution in a background thread.
     * @param inbound The queue to read from.
     * @param everyMillis The interval in milliseconds to wait between message drains.
     *                  This is used to control the frequency of message processing.
     */
    private void startDrainLoop() {

        Runnable[] taskReference = new Runnable[1];
        taskReference[0] = () -> {
            // Check if there are any messages to process.
            // If there are, process them and update the GUI.
            if (drainOnce()) {
                this.context.gui.getGUIThread().invokeLater(taskReference[0]);
            } else {
                this.timer.schedule(new TimerTask() {
                    @Override public void run() {
                        ChatClientTuiApp.this.context.gui.getGUIThread().invokeLater(taskReference[0]);
                    }
                }, this.everyMillis);
            }
        };
        this.context.gui.getGUIThread().invokeLater(taskReference[0]);
    }
}
