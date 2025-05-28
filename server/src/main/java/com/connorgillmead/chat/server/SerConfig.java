// SerConfig.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import jakarta.websocket.DeploymentException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Scanner;

/**
 * SerConfig is a record that holds the configuration for the chat server.
 * It contains the host name, port number, access token, and flags indicating
 * whether the host and port were provided as command-line arguments.
 * The record is immutable, meaning that once it is created, its values cannot be changed.
 * This is useful for ensuring that the configuration remains consistent throughout the program.
 * The record is used to pass configuration information to the chat server.
 * @param host The host name of the chat server.
 * @param port The port number of the chat server.
 * @param token The access token for authentication.
 *             This is used to verify the identity of the client connecting to the server.
 * @param hostGiven Flag indicating whether the host was provided as a command-line argument.
 *                 This is used to determine whether to prompt the user for a host name.
 * @param portGiven Flag indicating whether the port was provided as a command-line argument.
 *                 This is used to determine whether to prompt the user for a port number.
 */
public record SerConfig(String host, int port, String token, boolean hostGiven, boolean portGiven) {

    // Default port number for the chat server.
    // This is the port number that the server will listen on for incoming connections.
    private static final int DEFAULT_PORT = 5555;

    // Default listener port for the ancillary server.
    // This is the port number that the ancillary server will listen on for incoming WebSocket connections.
    private static final int LISTENER_PORT = 8081;

    /**
     * Creates a new SerConfig object from command-line arguments.
     * The method parses the command-line arguments and prompts the user for any missing values.
     * @param args Command-line arguments passed to the program.
     *             The first argument is the host name, the second is the port number,
     *            * the third is the access token.
     * @return A new SerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static SerConfig parseArgs(String[] args) {
        // Default values for host, port, and token.
        String host = "localhost";
        int port = DEFAULT_PORT;
        String token = null;

        // Used to specify if user enters default values.
        boolean hostGiven = false;
        boolean portGiven = false;

        // Begin looping through command-line arguments.
        for (int i = 0; i < args.length;) {
            switch (args[i]) {
                case "--host" -> {
                    host = args[i + 1];
                    hostGiven = true;
                    i += 2;
                }
                case "--port" -> {
                    port = Integer.parseInt(args[i + 1]);
                    portGiven = true;
                    i += 2;
                }
                case "--token" -> {
                    token = args[i + 1];
                    i += 2;
                }
                default -> {
                    port = Integer.parseInt(args[i]);
                    portGiven = true;
                    i += 1;
                }
            }
        }
        return new SerConfig(host, port, token, hostGiven, portGiven);
    }

    /**
     * Prompts the user for missing values.
     * This method is called if the user does not provide a host, port, or token as command-line arguments.
     * It prompts the user for the missing values and returns a new SerConfig object with the provided values.
     * @param in The SerConfig object containing the default values.
     *           This object is used to check if the user has provided values for host, port, and token.
     * @return A new SerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static SerConfig argumentPrompt(SerConfig in) {
        String h = in.host();
        int    p = in.port();
        String t = in.token();

        // Create a Scanner to read user input from the console.
        try (Scanner console = new Scanner(System.in, "UTF-8")) {

            // Check if the user has specified a host.
            if (!in.hostGiven) {
                System.out.print("Bind address [localhost]: ");
                String v = console.nextLine().trim();
                h = v.isEmpty() ? "localhost" : v;
            }

            // Check if the user has specified a port number.
            if (!in.portGiven) {
                System.out.printf("Port [%d]: ", DEFAULT_PORT);
                String v = console.nextLine().trim();
                if (!v.isEmpty()) {
                    p = Integer.parseInt(v);
                }
            }

            // Check if the expectedToken has not been set (null).
            if (t == null) {
                System.out.print("Access token (or press Enter for none): ");
                String v = console.nextLine().trim();
                t = v.isEmpty() ? null : v;
            }
        }
        return new SerConfig(h, p, t, true, true);
    }

    /**
     * Shuts down the server gracefully.
     * This method is called when the server is stopped or interrupted.
     * @param tcp The ChatServer instance representing the server socket.
     *            This instance is used to close the server socket and release resources.
     */
    public static void shutdownServer(ChatServer tcp) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown requested - closing server socket.");
            try {
                tcp.close();
            } catch (IOException ignored) {
            }
            ChatServerHub.append("[" + Instant.now() + "] SERVER_STOP");
        }));
    }

    /**
     * Starts the ancillary server for status monitoring.
     * This method creates a new ChatServerHub instance and starts the HTTP server.
     * It is used to provide a simple HTTP interface to check the server status and connected users.
     * @param cfg The SerConfig object containing the server configuration.
     *            This object contains the host name, port number, and access token.
     * @return A new ChatServerHub instance.
     *         The ChatServerHub is responsible for managing connected clients and broadcasting messages.
     * @throws IOException If an I/O error occurs when starting the HTTP server.
     *                     This may happen if the server cannot bind to the specified port.
     */
    public static ChatServerHub startAncillaryServer(SerConfig cfg) throws IOException, DeploymentException {
        ChatServerHub hub = new ChatServerHub();
        hub.setToken(cfg.token());
        StatusHttpServer.start(hub);
        // Start the WebSocket server.
        // The WebSocket server is used to provide real-time updates to connected clients.
        WebSocketChatEndpoint.attachHub(hub);

        org.glassfish.tyrus.server.Server wsServer =
            new org.glassfish.tyrus.server.Server(
                "localhost", LISTENER_PORT, "/", null,
                WebSocketChatEndpoint.class
            );
        try {
            wsServer.start();
            System.out.println("WebSocket server started at ws://localhost:8081/wschat");
        } catch (DeploymentException e) {
            e.printStackTrace();
            throw e;
        }

        // Append the server start message to the log.
        // This message indicates that the server has started successfully and is listening for connections.
        ChatServerHub.append("[" + Instant.now() + "] SERVER_START  "
                             + cfg.host() + ':' + cfg.port()
                             + "  token=" + (cfg.token() == null ? "<none>" : cfg.token()));
        return hub;
    }

    /**
     * Accepts incoming connections in a loop.
     * This method blocks until a client connects to the server.
     * It creates a new thread for each client connection and handles authentication and duplicate name checks.
     * @param cfg The SerConfig object containing the server configuration.
     *            This object contains the host name, port number, and access token.
     * @param hub The ChatServerHub instance managing connected clients.
     *            The ChatServerHub is responsible for broadcasting messages to all connected clients.
     * @param tcp The ChatServer instance representing the server socket.
     *            This instance is used to accept incoming connections from clients.
     * @throws IOException If an I/O error occurs when accepting a connection.
     *                     This may happen if the server socket is closed or an I/O error occurs.
     */
    public static void acceptLoop(SerConfig cfg,
                                  ChatServerHub hub,
                                  ChatServer tcp) throws IOException {
        while (true) {
            Socket socket = tcp.awaitConnection();

            // BufferedReader is used to read the input stream from the socket.
            // It reads the incoming data line by line.
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Read the first line from the input stream.
            // This line is expected to be a JSON message from the client.
            String firstLine = in.readLine();

            // If the first line is null, it means the client has closed the connection.
            // In this case, close the socket and continue to the next iteration of the loop.
            if (firstLine == null) {
                socket.close();
                continue;
            }

            // Parse the first line as a ChatMessage object.
            // The ChatMessage class is used to represent messages exchanged between the server and clients.
            ChatMessage hello = ChatMessage.fromJson(firstLine);

            // Check if the message is a valid hello message.
            if (cfg.token() != null && !cfg.token().equals(hello.getToken())) {
                sendErrorAndClose(socket, "Authentication has failed.");
                ChatServerHub.append("[%s] AUTH_FAIL from %s"
                        .formatted(Instant.now(), socket.getRemoteSocketAddress()));
                continue;
            }

            // Check for duplicate names.
            // If the name is already taken, send an error message and close the socket.
            String user = hello.getUser();
            if (!hub.reserveName(user)) {
                sendErrorAndClose(socket, "Username already taken.");
                continue;
            }

            // Create a new ChatMessage object to acknowledge the connection.
            // This message is sent back to the client to confirm the connection.
            System.out.println(user + " connected on " + socket);
            hub.broadcast(hello);

            // Create a new ClientHandler thread to handle the client connection.
            // The ClientHandler is responsible for processing messages from the client
            // and broadcasting them to other connected clients.
            new Thread(new ClientHandler(socket, hub, user)).start();
        }
    }

    /**
     * Sends an error message to the client and closes the socket.
     * This method is called when an error occurs during authentication or duplicate name checks.
     * @param s The socket representing the connection to the client.
     *          This socket is used to send the error message and close the connection.
     * @param msg The error message to send to the client.
     *            This message is sent in JSON format and indicates the reason for the error.
     */
    public static void sendErrorAndClose(Socket s, String msg) {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            pw.println(ChatMessage.error(msg).toJson());
        } catch (IOException ignored) { }
        try {
            s.close();
        } catch (IOException ignored) { }
    }
}

