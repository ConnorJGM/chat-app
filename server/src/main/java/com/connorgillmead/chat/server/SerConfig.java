// SerConfig.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import jakarta.websocket.DeploymentException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * SerConfig is a record that holds the configuration for the chat server.
 * It contains the host name, port number, access token, and flags indicating
 * whether the host and port were provided as command-line arguments.
 * The record is immutable, meaning that once it is created, its values cannot
 * be changed.
 * This is useful for ensuring that the configuration remains consistent
 * throughout the program.
 * The record is used to pass configuration information to the chat server.
 *
 * @param host      The host name of the chat server.
 * @param port      The port number of the chat server.
 * @param token     The access token for authentication.
 *                  This is used to verify the identity of the client connecting
 *                  to the server.
 * @param hostGiven Flag indicating whether the host was provided as a
 *                  command-line argument.
 *                  This is used to determine whether to prompt the user for a
 *                  host name.
 * @param portGiven Flag indicating whether the port was provided as a
 *                  command-line argument.
 *                  This is used to determine whether to prompt the user for a
 *                  port number.
 */
public record SerConfig(String host, int port, String token, boolean hostGiven, boolean portGiven) {

    // Default port number for the chat server.
    // This is the port number that the server will listen on for incoming
    // connections.
    private static final int DEFAULT_PORT = 5555;

    // Default listener port for the ancillary server.
    // This is the port number that the ancillary server will listen on for incoming
    // WebSocket connections.
    private static final int LISTENER_PORT = 8081;

    // Path to the configuration file.
    // This file is used to store the server configuration, such as host, port,
    // and access token.
    private static final Path CONFIG_FILE = Path.of("server_config.properties");

    // Shutdown hook thread for the server.
    // This thread is used to gracefully shut down the server when the JVM exits.
    private static Thread shutdownHook;

    /**
     * Creates a new SerConfig object from command-line arguments.
     * The method parses the command-line arguments and prompts the user for any
     * missing values.
     *
     * @param args Command-line arguments passed to the program.
     *             The first argument is the host name, the second is the port
     *             number,
     *             * the third is the access token.
     * @return A new SerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static SerConfig parseArgs(String[] args) {
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        boolean autoStart = argList.remove("--auto-start");

        // Default values for host, port, and token.
        String host = "localhost";
        int port = DEFAULT_PORT;
        String token = null;
        // Used to specify if user enters default values.
        boolean hostGiven = false;
        boolean portGiven = false;

        // If autoStart is true, load the configuration from the properties file.
        // This allows the server to start with the last used configuration without
        // requiring the user to provide command-line arguments.
        if (autoStart && Files.exists(CONFIG_FILE)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(CONFIG_FILE)) {
                properties.load(input);
            } catch (IOException e) {
                System.err.println("Failed to load configuration: " + e.getMessage());
            }
            host = properties.getProperty("host", host);
            port = Integer.parseInt(properties.getProperty("port", String.valueOf(port)));
            String rawToken = properties.getProperty("token", "").trim();
            token = rawToken.isEmpty() ? null : rawToken;
            hostGiven = true;
            portGiven = true;
        } else {
            // Begin looping through command-line arguments.
            for (int i = 0; i < argList.size();) {
                switch (argList.get(i)) {
                    case "--host" -> {
                        host = argList.get(i + 1);
                        hostGiven = true;
                        i += 2;
                    }
                    case "--port" -> {
                        port = Integer.parseInt(argList.get(i + 1));
                        portGiven = true;
                        i += 2;
                    }
                    case "--token" -> {
                        token = argList.get(i + 1);
                        i += 2;
                    }
                    default -> {
                        i++;
                    }
                }
            }
        }
        return new SerConfig(host, port, token, hostGiven, portGiven);
    }

    /**
     * Saves the server configuration to a properties file.
     * This method writes the host, port, and token to a properties file named
     * server_config.properties.
     * If the token is null, it saves an empty string for the token.
     * @param config The SerConfig object containing the server configuration.
     *               This object contains the host name, port number, and access token.
     */
    public static void save(SerConfig config) {
        Properties properties = new Properties();
        properties.setProperty("host", config.host());
        properties.setProperty("port", String.valueOf(config.port()));
        String token = config.token() == null ? "" : config.token();
        properties.setProperty("token", token);
        try (OutputStream output = Files.newOutputStream(CONFIG_FILE)) {
            properties.store(output, "Chat Server Configuration");
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }

    /**
     * Prompts the user for missing values.
     * This method is called if the user does not provide a host, port, or token as
     * command-line arguments.
     * It prompts the user for the missing values and returns a new SerConfig object
     * with the provided values.
     *
     * @param in The SerConfig object containing the default values.
     *           This object is used to check if the user has provided values for
     *           host, port, and token.
     * @return A new SerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static SerConfig argumentPrompt(SerConfig input, Scanner console) {
        String host = input.host();
        int port = input.port();
        String token = input.token();

        // Check if the user has specified a host.
        if (!input.hostGiven) {
            System.out.print("Bind address [" + host + "]: ");
            String line = console.hasNextLine()
                    ? console.nextLine().trim()
                    : "";
            host = line.isEmpty() ? "localhost" : line;
        }

        // Check if the user has specified a port number.
        if (!input.portGiven) {
            System.out.printf("Port [%d]: ", port);
            String line = console.hasNextLine()
                    ? console.nextLine().trim()
                    : "";
            if (!line.isEmpty()) {
                try {
                    port = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number. Using default port " + DEFAULT_PORT);
                }
            }
        }

        // Check if the expectedToken has not been set (null).
        if (token == null) {
            System.out.print("Access token (or press Enter for none): ");
            String line = console.hasNextLine()
                    ? console.nextLine().trim()
                    : "";
            token = line.isEmpty() ? null : line;
        }
        return new SerConfig(host, port, token, true, true);
    }

    /**
     * Shuts down the server gracefully.
     * This method is called when the server is stopped or interrupted.
     *
     * @param tcp The ChatServer instance representing the server socket.
     *            This instance is used to close the server socket and release
     *            resources.
     */
    public static void shutdownServer(ChatServer tcp) {
        shutdownHook = new Thread(() -> {
            System.out.println("Shutdown requested - closing server socket.");
            try {
                tcp.close();
            } catch (IOException ignored) {
            }
            ChatServerHub.append("[" + Instant.now() + "] SERVER_STOP");
        }, "ChatServerShutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Removes the shutdown hook for the server.
     * This method is called when the server is stopped or interrupted.
     * It removes the shutdown hook thread that was added to the runtime.
     * This is useful to prevent the shutdown hook from being called multiple times
     * if the server is restarted.
     */
    public static void removeShutdownHook() {
        if (shutdownHook != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }

    /**
     * Starts the ancillary server for status monitoring.
     * This method creates a new ChatServerHub instance and starts the HTTP server.
     * It is used to provide a simple HTTP interface to check the server status and
     * connected users.
     *
     * @param cfg The SerConfig object containing the server configuration.
     *            This object contains the host name, port number, and access token.
     * @return A new ChatServerHub instance.
     *         The ChatServerHub is responsible for managing connected clients and
     *         broadcasting messages.
     * @throws IOException If an I/O error occurs when starting the HTTP server.
     *                     This may happen if the server cannot bind to the
     *                     specified port.
     */
    public static ChatServerHub startAncillaryServer(SerConfig cfg) throws IOException, DeploymentException {
        ChatServerHub hub = new ChatServerHub();
        hub.setToken(cfg.token());
        ChatHttpServer.start(hub, cfg);
        // Start the WebSocket server.
        // The WebSocket server is used to provide real-time updates to connected
        // clients.
        WebSocketChatEndpoint.attachHub(hub);

        org.glassfish.tyrus.server.Server wsServer = new org.glassfish.tyrus.server.Server(
                "localhost", LISTENER_PORT, "/", null,
                WebSocketChatEndpoint.class);
        try {
            wsServer.start();
            System.out.println("WebSocket server started at ws://localhost:8081/wschat");
        } catch (DeploymentException e) {
            e.printStackTrace();
            throw e;
        }

        // Append the server start message to the log.
        // This message indicates that the server has started successfully and is
        // listening for connections.
        ChatServerHub.append("[" + Instant.now() + "] SERVER_START  "
                + cfg.host() + ':' + cfg.port()
                + "  token=" + (cfg.token() == null ? "<none>" : cfg.token()));
        return hub;
    }

    /**
     * Accepts incoming connections in a loop.
     * This method blocks until a client connects to the server.
     * It creates a new thread for each client connection and handles authentication
     * and duplicate name checks.
     *
     * @param cfg The SerConfig object containing the server configuration.
     *            This object contains the host name, port number, and access token.
     * @param hub The ChatServerHub instance managing connected clients.
     *            The ChatServerHub is responsible for broadcasting messages to all
     *            connected clients.
     * @param tcp The ChatServer instance representing the server socket.
     *            This instance is used to accept incoming connections from clients.
     * @throws IOException If an I/O error occurs when accepting a connection.
     *                     This may happen if the server socket is closed or an I/O
     *                     error occurs.
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
            // In this case, close the socket and continue to the next iteration of the
            // loop.
            if (firstLine == null) {
                socket.close();
                continue;
            }

            // Parse the first line as a ChatMessage object.
            // The ChatMessage class is used to represent messages exchanged between the
            // server and clients.
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
     * This method is called when an error occurs during authentication or duplicate
     * name checks.
     *
     * @param s   The socket representing the connection to the client.
     *            This socket is used to send the error message and close the
     *            connection.
     * @param msg The error message to send to the client.
     *            This message is sent in JSON format and indicates the reason for
     *            the error.
     */
    public static void sendErrorAndClose(Socket s, String msg) {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {
            pw.println(ChatMessage.error(msg).toJson());
        } catch (IOException ignored) {
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
