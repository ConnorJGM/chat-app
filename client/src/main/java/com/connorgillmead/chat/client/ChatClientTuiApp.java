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
        Screen screen = new DefaultTerminalFactory().createScreen();
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

                // 3) Store into the holder and close
                configHolder.set(new CliConfig(host, port, user, token));
                configWindow.close();

            } catch (NumberFormatException ex) {
                new MessageDialogBuilder()
                        .setTitle("Error")
                        .setText("Port must be a number")
                        .build()
                        .showDialog(gui);
            }
        });

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
        launchChatGui(gui, cfg);
    }

    // This method launches the chat GUI.
    // It creates a new BasicWindow object to display the chat interface.
    // It uses a MultiWindowTextGUI object to create the user interface.
    // The method takes a MultiWindowTextGUI object and a CliConfig object as parameters.
    private static void launchChatGui(MultiWindowTextGUI gui, CliConfig cfg) {
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

            // Create a new TextBox object to display the chat messages.
            // This text box is used to display the chat messages in a multi-line format.
            // It is set to read-only mode to prevent user input.
            TextBox log = new TextBox(new TerminalSize(COL, ROW),
                                     TextBox.Style.MULTI_LINE)
                                     .setReadOnly(true);
            root.addComponent(log, BorderLayout.Location.CENTER);

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
                    return super.handleKeyStroke(k);
                }
            }.setPreferredSize(new TerminalSize(COL, 1));
            root.addComponent(input, BorderLayout.Location.BOTTOM);

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

            // Create a timer to drain the inbound queue.
            // This timer is used to periodically check for new messages in the queue.
            Timer guiTimer = new Timer("drain reschedule", true);

            // This is a runnable that drains the inbound queue.
            // It checks for new messages in the queue and updates the chat log.
            Runnable[] drain = new Runnable[1];

            // This is a lambda expression that implements the Runnable interface.
            // It is used to drain the inbound queue and update the chat log.
            drain[0] = new Runnable() {
                @Override public void run() {
                    ChatMessage m = inbound.poll();
                    if (m != null) {
                        switch (m.getType()) {
                            case "text"  -> {
                                log.addLine(m.getUser() + ": " + m.getBody());
                                log.setCaretPosition(log.getLineCount(), 0);
                            }
                            case "error" -> {
                                log.addLine("*** " + m.getBody() + " ***");
                                guiTimer.cancel();
                                window.close();
                                return;
                            }
                            default -> { }
                        }
                        gui.getGUIThread().invokeLater(this);
                    } else {
                        guiTimer.schedule(new TimerTask() {
                            @Override public void run() {
                                gui.getGUIThread().invokeLater(drain[0]);
                            }
                        }, DRAIN);
                    }
                }
            };

            // Schedule the first run of the drain task.
            // This is done to start draining the inbound queue.
            gui.getGUIThread().invokeLater(drain[0]);

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
}
