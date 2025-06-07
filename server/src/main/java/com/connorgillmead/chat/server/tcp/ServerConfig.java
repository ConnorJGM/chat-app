// ServerConfig.java

package com.connorgillmead.chat.server.tcp;

import com.connorgillmead.chat.common.ChatMessage;
import com.connorgillmead.chat.server.http.ChatHttpServer;
import com.connorgillmead.chat.server.websocket.WebSocketChatEndpoint;
import jakarta.websocket.DeploymentException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/**
 * ServerConfig is a record that holds the configuration for the chat server.
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
public record ServerConfig(String host, int port, String token, boolean hostGiven, boolean portGiven) {

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
     * Creates a new ServerConfig object from command-line arguments.
     * The method parses the command-line arguments and prompts the user for any
     * missing values.
     *
     * @param arguments Command-line arguments passed to the program.
     *             The first argument is the host name, the second is the port
     *             number,
     *             * the third is the access token.
     * @return A new ServerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static ServerConfig parseArgs(String[] arguments) {
        List<String> argumentList = new ArrayList<>(Arrays.asList(arguments));
        boolean autoStart = argumentList.remove("--auto-start");

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
            try (InputStream inputStream = Files.newInputStream(CONFIG_FILE)) {
                properties.load(inputStream);
            } catch (IOException error) {
                System.err.println("Failed to load configuration: " + error.getMessage());
            }
            host = properties.getProperty("host", host);
            port = Integer.parseInt(properties.getProperty("port", String.valueOf(port)));
            String rawToken = properties.getProperty("token", "").trim();
            token = rawToken.isEmpty() ? null : rawToken;
            hostGiven = true;
            portGiven = true;
        } else {
            // Begin looping through command-line arguments.
            for (int i = 0; i < argumentList.size();) {
                switch (argumentList.get(i)) {
                    case "--host" -> {
                        host = argumentList.get(i + 1);
                        hostGiven = true;
                        i += 2;
                    }
                    case "--port" -> {
                        port = Integer.parseInt(argumentList.get(i + 1));
                        portGiven = true;
                        i += 2;
                    }
                    case "--token" -> {
                        token = argumentList.get(i + 1);
                        i += 2;
                    }
                    default -> {
                        i++;
                    }
                }
            }
        }
        return new ServerConfig(host, port, token, hostGiven, portGiven);
    }

    /**
     * Saves the server configuration to a properties file.
     * This method writes the host, port, and token to a properties file named
     * server_config.properties.
     * If the token is null, it saves an empty string for the token.
     * @param config The ServerConfig object containing the server configuration.
     *               This object contains the host name, port number, and access token.
     */
    public static void save(ServerConfig config) {
        Properties properties = new Properties();
        properties.setProperty("host", config.host());
        properties.setProperty("port", String.valueOf(config.port()));
        String token = config.token() == null ? "" : config.token();
        properties.setProperty("token", token);
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_FILE)) {
            properties.store(outputStream, "Chat Server Configuration");
        } catch (IOException error) {
            System.err.println("Failed to save configuration: " + error.getMessage());
        }
    }

    /**
     * Prompts the user for missing values.
     * This method is called if the user does not provide a host, port, or token as
     * command-line arguments.
     * It prompts the user for the missing values and returns a new ServerConfig object
     * with the provided values.
     *
     * @param serverConfig The ServerConfig object containing the default values.
     *           This object is used to check if the user has provided values for
     *           host, port, and token.
     * @return A new ServerConfig object with the provided or default values.
     *         The object contains the host name, port number, and access token.
     */
    public static ServerConfig argumentPrompt(ServerConfig serverConfig, Scanner scanner) {
        String host = serverConfig.host();
        int port = serverConfig.port();
        String token = serverConfig.token();

        // Check if the user has specified a host.
        if (!serverConfig.hostGiven) {
            System.out.print("Bind address [" + host + "]: ");
            String line = scanner.hasNextLine()
                    ? scanner.nextLine().trim()
                    : "";
            host = line.isEmpty() ? "localhost" : line;
        }

        // Check if the user has specified a port number.
        if (!serverConfig.portGiven) {
            System.out.printf("Port [%d]: ", port);
            String line = scanner.hasNextLine()
                    ? scanner.nextLine().trim()
                    : "";
            if (!line.isEmpty()) {
                try {
                    port = Integer.parseInt(line);
                } catch (NumberFormatException error) {
                    System.out.println("Invalid port number. Using default port " + DEFAULT_PORT);
                }
            }
        }

        // Check if the expectedToken has not been set (null).
        if (token == null) {
            System.out.print("Access token (or press Enter for none): ");
            String line = scanner.hasNextLine()
                    ? scanner.nextLine().trim()
                    : "";
            token = line.isEmpty() ? null : line;
        }
        return new ServerConfig(host, port, token, true, true);
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
            } catch (IOException error) {
                error.printStackTrace();
            }
            ChatServerHub.append("[" + ChatServerHub.getCurrentTime() + "] SERVER_STOP");
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
     * @param cfg The ServerConfig object containing the server configuration.
     *            This object contains the host name, port number, and access token.
     * @return A new ChatServerHub instance.
     *         The ChatServerHub is responsible for managing connected clients and
     *         broadcasting messages.
     * @throws IOException If an I/O error occurs when starting the HTTP server.
     *                     This may happen if the server cannot bind to the
     *                     specified port.
     */
    public static ChatServerHub startAncillaryServer(ServerConfig config) throws IOException, DeploymentException {
        ChatServerHub hub = new ChatServerHub();
        hub.setToken(config.token());
        ChatHttpServer.start(hub, config);
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
        } catch (DeploymentException error) {
            error.printStackTrace();
            throw error;
        }

        // Append the server start message to the log.
        // This message indicates that the server has started successfully and is
        // listening for connections.
        ChatServerHub.append("[" + ChatServerHub.getCurrentTime() + "] SERVER_START  "
                + config.host() + ':' + config.port()
                + "  token=" + (config.token() == null || config.token().isBlank() ? "<none>" : "<set>"));
        return hub;
    }

    /**
     * Accepts incoming connections in a loop.
     * This method blocks until a client connects to the server.
     * It creates a new thread for each client connection and handles authentication
     * and duplicate name checks.
     *
     * @param cfg The ServerConfig object containing the server configuration.
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
    public static void acceptLoop(ServerConfig config,
            ChatServerHub hub,
            ChatServer tcp) throws IOException {
        while (true) {
            try {
                SSLSocket sslSocket = (SSLSocket) tcp.awaitConnection();
                sslSocket.setUseClientMode(false);
                sslSocket.startHandshake();

                // Create a new ClientHandler thread to handle the client connection.
                // The ClientHandler is responsible for processing messages from the client
                // and broadcasting them to other connected clients.
                new Thread(new ClientHandler(sslSocket, hub)).start();

            } catch (SSLException error) {
                String message = error.getMessage();
                if (message != null && message.contains("Remote host terminated the handshake")) {
                    continue;
                }
                System.err.println("SSL error: " + error.getMessage());
            } catch (SocketException error) {
                String message = error.getMessage();
                if (message != null && message.contains("Socket closed")) {
                    // If the socket is closed, we can ignore this error.
                    continue;
                }
                System.err.println("Socket error: " + error.getMessage());
            } catch (IOException error) {
                System.err.println("I/O error: " + error.getMessage());
            }
        }
    }

    /**
     * Sends an error message to the client and closes the socket.
     * This method is called when an error occurs during authentication or duplicate
     * name checks.
     *
     * @param socket  The socket representing the connection to the client.
     *                  This socket is used to send the error message and close the
     *                  connection.
     * @param message The error message to send to the client.
     *                  This message is sent in JSON format and indicates the reason for
     *                  the error.
     */
    public static void sendErrorAndClose(Socket socket, String message) {
        try (PrintWriter printWriter = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            printWriter.println(ChatMessage.error(message).toJson());
        } catch (IOException error) {
            error.printStackTrace();
        }
        try {
            socket.close();
        } catch (IOException error) {
            error.printStackTrace();
        }
    }
}
