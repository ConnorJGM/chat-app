// ChatServerHub.java

package com.connorgillmead.chat.server.tcp;

import com.connorgillmead.chat.server.database.Database;
import jakarta.websocket.DeploymentException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Starts the TCP listener and spins up a thread for each client.
 */
public final class ChatServerApp {

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains a main method.
     */
    private ChatServerApp() {
    }

    /**
     * Main method to start the server.
     *
     * @param arguments Command line arguments. The first argument is the port number
     *             (default is 5555).
     * @throws IOException              If an I/O error occurs when creating the
     *                                  server socket or accepting a connection.
     * @throws GeneralSecurityException If a security error occurs when creating the
     *                                  server socket.
     */
    public static void main(String[] arguments) throws IOException, GeneralSecurityException {

        // Create a Scanner object to read user input from the scanner.
        Scanner scanner = new Scanner(System.in, "UTF-8");

        // Parse command line arguments to get the server configuration.
        // The ServerConfig.parseArgs method is used to parse the command line arguments
        // and create a ServerConfig object that contains the host, port, and token.
        List<String> mainArgs = new ArrayList<>(Arrays.asList(arguments));
        boolean autoStart = mainArgs.remove("--auto-start");
        ServerConfig config = ServerConfig.parseArgs(arguments);

        // If the host, port, or token is not provided, prompt the user for input.
        // The ServerConfig.argumentPrompt method is used to prompt the user for missing
        if (!config.hostGiven() || !config.portGiven() || (config.token() == null || config.token().isEmpty())) {
            if (!autoStart) {
                config = ServerConfig.argumentPrompt(config, scanner);
                ServerConfig.save(config);
            }
        }

        try {
            System.out.println("Creating database....");
            Database.chatDatabase();
            System.out.println("Database created successfully.");
        } catch (SQLException error) {
            System.err.println("Failed to create database: " + error.getMessage());
            error.printStackTrace();
            return;
        }

        // Start the server by prompting the user for input.
        // The user is prompted to type 'start' to start the server or 'quit' to exit.
        if (!autoStart) {
            System.out.println("Type 'start' to start the server, 'quit' to exit.");
            String command;
            while (scanner.hasNextLine()) {
                command = scanner.nextLine().trim().toLowerCase();
                if ("start".equals(command)) {
                    break;
                } else if ("quit".equals(command)) {
                    System.out.println("Exiting without starting the server.");
                    return;
                } else {
                    System.out.println("Unknown command: " + command);
                }
            }
        }

        // Create a new ChatServer instance to listen for incoming connections.
        // The ChatServer is a TCP server that listens for incoming connections on the
        // specified port and host.
        // The ChatServer also handles the server's shutdown process.
        try (ChatServer tcp = new ChatServer(config.port(), config.host())) {

            // Shutdown the server if it is already running.
            // The shutdownServer method is used to close the server socket and release
            // resources.
            ServerConfig.shutdownServer(tcp);

            // Create a new ChatServerHub instance to manage connected clients.
            // The ChatServerHub is responsible for broadcasting messages to all connected
            // clients.
            ChatServerHub hub = ServerConfig.startAncillaryServer(config);

            // Print the server information to the scanner.
            // The server information includes the host, port, and token (if any).
            System.out.printf("Web server (HTTP/WebSocket) running at http://%s:8080/\n", config.host());
            System.out.printf("WebSocket endpoint at ws://%s:8081/wschat\n", config.host());
            System.out.printf("Chat server running on %s:%d  token = %s\n",
                    config.host(), config.port(), config.token() == null ? "<none>" : config.token());

            System.out.println("Type 'kick <username>' to kick a user, 'quit' to shutdown server.");

            // Admin commands thread to handle kicking users and shutting down the server.
            // This thread listens for commands from the scanner and performs actions based
            // on the input.
            Thread admin = new Thread(() -> {
                while (scanner.hasNextLine()) {
                    final int kickLength = 5;
                    String line = scanner.nextLine().trim();
                    if (line.toLowerCase().startsWith("quit")) {
                        hub.serverShutdown(line);
                        System.exit(0);
                    } else if (line.startsWith("kick ")) {
                        String user = line.substring(kickLength).trim();
                        hub.kickUser(user);
                        System.out.println("Kicked user: " + user);
                    } else {
                        System.out.println("Unknown command: " + line);
                    }
                }
            }, "Admin Commands");
            admin.setDaemon(true);
            admin.start();

            ServerConfig.acceptLoop(config, hub, tcp);

        } catch (DeploymentException | IOException error) {
            System.err.println("Connection aborted.");
        }
    }
}
