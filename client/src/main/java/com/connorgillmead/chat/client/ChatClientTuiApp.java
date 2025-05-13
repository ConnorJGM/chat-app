// ChatClientTuiApp.java

package com.connorgillmead.chat.client;

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
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    private static final int COL = 80;
    private static final int COL_LOG = 60;
    private static final int COL_LIST = 20;
    private static final int ROW = 20;
    private static final int DRAIN = 100;

    // Private constructor to prevent instantiation.
    private ChatClientTuiApp() {
    }

    /**
     * Main method to start the lanterna client.
     * It connects to the server and starts two threads: one for sending messages and another for receiving messages.
     * @param args The first argument is the hostname, and the second argument is the port number.
     * @throws IOException If an I/O error occurs when creating the socket or transferring data.
     * @throws GeneralSecurityException If a security error occurs when creating the socket.
     *         This can happen if the SSL/TLS protocol is not supported or if the trust manager cannot be initialised.
     */
    public static void main(String[] args) throws Exception, IOException {
        try {
            runLanternaClient();
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // This method runs the chat client in lanterna mode.
    // It uses the Lanterna library to create a text-based user interface (TUI).
    // It connects to the server and starts two threads: one for sending messages and another for receiving messages.
    // The method takes a MultiWindowTextGUI object and a CliConfig object as parameters.
    // The MultiWindowTextGUI object is used to create the user interface, and the CliConfig object contains the host,
    // port, user, and token information.
    // The method prompts the user for connection details (host, port, username, and token)
    // and then connects to the server.
    // It creates a new BasicWindow object to display the chat interface.
    private static void runLanternaClient() throws IOException, GeneralSecurityException {

        // Create a new terminal screen using the DefaultTerminalFactory.
        // This is used to create a text-based user interface (TUI).
        Screen screen = new DefaultTerminalFactory()
                            .setTerminalEmulatorTitle("Chat Client")
                            .createScreen();
        screen.startScreen();
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

        // Create a holder for the config.
        // This is used to store the configuration details entered by the user.
        AtomicReference<CliConfig> configHolder = new AtomicReference<>();

        // Build the config window.
        // This window prompts the user for connection details (host, port, username, and token).
        // It uses a GridLayout to arrange the components in a grid.
        final BasicWindow configWindow = new BasicWindow("Connect to Chat");
        Panel form = new Panel(new GridLayout(2));
        TextBox hostBox  = new TextBox().setText("localhost");
        TextBox portBox  = new TextBox().setText(String.valueOf(CliConfig.defaultPort()));
        TextBox userBox  = new TextBox();
        TextBox tokenBox = new TextBox();

        // Add labels and text boxes to the form.
        // The labels are used to describe the purpose of each text box.
        form.addComponent(new Label("Host:"));
        form.addComponent(hostBox);
        form.addComponent(new Label("Port:"));
        form.addComponent(portBox);
        form.addComponent(new Label("Username:"));
        form.addComponent(userBox);
        form.addComponent(new Label("Token (opt):"));
        form.addComponent(tokenBox);

        // Add a connect button to the form.
        // This button is used to connect to the chat server.
        Button connect = new Button("Connect", () -> {
            try {
                String host  = hostBox .getText().trim();
                int    port  = Integer.parseInt(portBox.getText().trim());
                String user  = userBox .getText().trim();
                String token = tokenBox.getText().trim().isEmpty()
                            ? null
                            : tokenBox.getText().trim();

                // Set the config in the holder.
                // This is used to store the configuration details entered by the user.
                CliConfig cfg = CliConfig.validateCreate(host, port, user, token);
                configHolder.set(cfg);
                configWindow.close();

            // If the user does not enter a valid host, port, or username,
            // show an error message dialog.
            } catch (NumberFormatException ex) {
                new MessageDialogBuilder()
                        .setTitle("Invalid Port")
                        .setText("Port must be a number between 1 and 65535.")
                        .addButton(MessageDialogButton.OK)
                        .build()
                        .showDialog(gui);
            } catch (IllegalArgumentException e) {
                new MessageDialogBuilder()
                        .setTitle("Invalid Input")
                        .setText(e.getMessage())
                        .addButton(MessageDialogButton.OK)
                        .build()
                        .showDialog(gui);
            }
        });

        // Add empty space and the connect button to the form.
        // This is done to separate the components in the form.
        form.addComponent(
            new EmptySpace(new TerminalSize(0, 1)),
                GridLayout.createHorizontallyFilledLayoutData(2)
        );
        form.addComponent(
            connect,
            GridLayout.createHorizontallyEndAlignedLayoutData(2)
        );

        // Set the form to the config window.
        // This window is used to prompt the user for connection details.
        configWindow.setComponent(form);

        // Block until the user clicks connect.
        gui.addWindowAndWait(configWindow);

        // Get the config from the holder.
        // This is the configuration details entered by the user.
        CliConfig cfg = configHolder.get();

        // Launch the chat GUI.
        launchChatGui(gui, screen, cfg);
    }

    // This method launches the chat GUI.
    // It creates a new BasicWindow object to display the chat interface.
    // It uses a MultiWindowTextGUI object to create the user interface.
    // The method takes a MultiWindowTextGUI object and a CliConfig object as parameters.
    private static void launchChatGui(MultiWindowTextGUI gui, Screen screen, CliConfig cfg) {
        try (ChatClient client = new ChatClient(cfg.host(), cfg.port())) {
            Socket socket = client.socket();

            // Thread A – send messages.
            // This thread sends messages to the server.
            PrintWriter out = new PrintWriter(
                socket.getOutputStream(), true, StandardCharsets.UTF_8);

            // Thread B – receive messages.
            // This thread reads messages from the server and puts them into a blocking queue.
            BlockingQueue<ChatMessage> inbound = new LinkedBlockingQueue<>();
            new Thread(() -> ChatClientNet.readLoop(socket, inbound)).start();

            // Create a new BasicWindow object to display the chat interface.
            // This window is used to display the chat messages and user input.
            BasicWindow window = new BasicWindow("Chat - " + cfg.host());

            // New panel for the chat interface.
            // This panel is used to arrange the components in the window.
            Panel root = new Panel(new BorderLayout());
            Panel side = new Panel(new GridLayout(1));
            side.setPreferredSize(new TerminalSize(COL_LIST, ROW));

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
            TextBox input = new TextBox() {
                @Override public Result handleKeyStroke(KeyStroke k) {
                    if (k.getKeyType() == KeyType.Enter) {
                        String msg = getText().trim();
                        if (!msg.isEmpty()) {
                            out.println(ChatMessage.of(cfg.user(), msg).toJson());
                        }
                        setText("");
                        return Result.HANDLED;
                    }
                    if (k.getKeyType() == KeyType.Escape) {
                        askQuit(gui, this, socket, screen);
                        return Result.HANDLED;
                    }
                    return super.handleKeyStroke(k);
                }
            }.setPreferredSize(new TerminalSize(COL, 1));
            root.addComponent(input, BorderLayout.Location.BOTTOM);
            userList.setEnabled(false);

            // Create a new TextBox object to display the chat messages.
            // This text box is used to display the chat messages in a multi-line format.
            // It is set to read-only mode to prevent user input.
            TextBox log = new TextBox(new TerminalSize(COL_LOG, ROW),
                                     TextBox.Style.MULTI_LINE) {
                @Override
                public Result handleKeyStroke(KeyStroke k) {
                    Result r = super.handleKeyStroke(k);
                    gui.getGUIThread().invokeLater(input::takeFocus);
                    return r;
                    }
            }.setReadOnly(true);
            root.addComponent(log, BorderLayout.Location.CENTER);

            // Set root panel to the window.
            // This panel is used to arrange the components in the window.
            window.setComponent(root);
            gui.addWindow(window);

            // Focus on the input box.
            // This is done to allow the user to start typing immediately.
            gui.getGUIThread().invokeLater(input::takeFocus);

            // Notify the server that the user has joined the chat.
            // This is done by sending a "hello" message to the server.
            out.println(ChatMessage.hello(cfg.user(), cfg.token()).toJson());

            // Start the message drain loop.
            // This loop reads messages from the server and displays them in the chat interface.
            // It uses a Timer to schedule the message drain operation at regular intervals.
            // The Timer is used to schedule tasks for future execution in a background thread.
            Timer guiTimer = new Timer("drain", true);

            // Schedule the message drain operation at regular intervals.
            // The drainOnce method is called to read messages from the server and display them in the chat interface.
            startDrainLoop(gui, guiTimer,
                           inbound,
                           log,
                           userList,
                           DRAIN);

            // Close the socket when the window is closed.
            // This is done to release any resources associated with the socket.
            gui.addWindowAndWait(window);
            socket.close();

        // Catch any exceptions that occur during the process.
        // This includes GeneralSecurityException and IOException.
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
            System.exit(1);
        }
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
    private static void doQuit(MultiWindowTextGUI gui,
                               Socket socket,
                               Screen screen) {
        try {
            socket.close();
        } catch (IOException ignored) { }
        try {
            screen.stopScreen();
        } catch (IOException ignored) { }
        gui.getGUIThread().invokeLater(() -> System.exit(0));
    }

    /**
     * askQuit(...) prompts the user to confirm quitting the application.
     * It displays a message dialog asking if the user is sure they want to quit.
     * If the user confirms, it calls doQuit(...) to close the application.
     * If the user cancels, it refocuses the input box.
     * @param gui The GUI to update.
     * @param input The input box to refocus.
     * @param socket The socket to close.
     * @param screen The screen to stop.
     *              This screen is used to display the chat interface.
     */
    private static void askQuit(MultiWindowTextGUI gui, TextBox input, Socket socket, Screen screen) {
        MessageDialogButton ans = new MessageDialogBuilder()
            .setTitle("Quit")
            .setText("Are you sure you want to quit?")
            .addButton(MessageDialogButton.Yes)
            .addButton(MessageDialogButton.No)
            .build()
            .showDialog(gui);

        if (ans == MessageDialogButton.Yes) {
            doQuit(gui, socket, screen);
        } else {
            gui.getGUIThread().invokeLater(input::takeFocus);
        }
    }

    /**
     * drainOnce(...) reads a single message from the queue and updates the GUI.
     * It handles different message types: "text", "roster", and "error".
     * It updates the log and user list accordingly.
     * @param inbound The queue to read from.
     * @param log The text box to display chat messages.
     * @param userList The table to display the user list.
     * @return true if a message was processed, false otherwise.
     *         This method is used to read messages from the server and update the GUI.
     */
    private static boolean drainOnce(BlockingQueue<ChatMessage> inbound,
                                 TextBox log,
                                 Table<String> userList) {

        ChatMessage m = inbound.poll();
        if (m == null) {
            return false;
        }

        switch (m.getType()) {
            case "text"   -> {
                log.addLine(m.getUser() + ": " + m.getBody());
                log.setCaretPosition(log.getLineCount(), 0);
            }
            case "roster" -> {
                userList.getTableModel().clear();
                m.getUserList().forEach(userList.getTableModel()::addRow);
                userList.invalidate();
            }
            case "error"  -> {
                log.addLine("*** " + m.getBody() + " ***");
            }
            default -> { }
        }
        return true;
    }

    /**
     * startDrainLoop(...) starts a loop that drains messages from the queue.
     * It uses a Timer to schedule the message drain operation at regular intervals.
     * @param gui The GUI to update.
     * @param timer The timer to use for scheduling.
     *             This timer is used to schedule tasks for future execution in a background thread.
     * @param inbound The queue to read from.
     * @param log The text box to display chat messages.
     * @param userList The table to display the user list.
     * @param everyMillis The interval in milliseconds to wait between message drains.
     *                  This is used to control the frequency of message processing.
     */
    private static void startDrainLoop(MultiWindowTextGUI gui,
                                   Timer timer,
                                   BlockingQueue<ChatMessage> inbound,
                                   TextBox log,
                                   Table<String> userList,
                                   int everyMillis) {

        Runnable[] taskRef = new Runnable[1];
        taskRef[0] = () -> {
            // Check if there are any messages to process.
            // If there are, process them and update the GUI.
            if (drainOnce(inbound, log, userList)) {
                gui.getGUIThread().invokeLater(taskRef[0]);
            } else {
                timer.schedule(new TimerTask() {
                    @Override public void run() {
                        gui.getGUIThread().invokeLater(taskRef[0]);
                    }
                }, everyMillis);
            }
        };
        gui.getGUIThread().invokeLater(taskRef[0]);
    }
}
