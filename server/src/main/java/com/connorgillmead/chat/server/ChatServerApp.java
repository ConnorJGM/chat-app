// ChatServerHub.java

package com.connorgillmead.chat.server;

import jakarta.websocket.DeploymentException;
import java.io.IOException;
import java.security.GeneralSecurityException;

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
     * @param args Command line arguments. The first argument is the port number (default is 5555).
     * @throws IOException If an I/O error occurs when creating the server socket or accepting a connection.
     * @throws GeneralSecurityException If a security error occurs when creating the server socket.
     */
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        // Parse command line arguments and create a SerConfig object.
        // The SerConfig class is responsible for handling server configuration.
        SerConfig cfg = SerConfig.argumentPrompt(SerConfig.parseArgs(args));

        // Create a new ChatServer instance to listen for incoming connections.
        // The ChatServer is a TCP server that listens for incoming connections on the specified port and host.
        // The ChatServer also handles the server's shutdown process.
        try (ChatServer tcp = new ChatServer(cfg.port(), cfg.host())) {

            // Shutdown the server if it is already running.
            // The shutdownServer method is used to close the server socket and release resources.
            SerConfig.shutdownServer(tcp);

            // Create a new ChatServerHub instance to manage connected clients.
            // The ChatServerHub is responsible for broadcasting messages to all connected clients.
            ChatServerHub hub = SerConfig.startAncillaryServer(cfg);

            // Print the server information to the console.
            // The server information includes the host, port, and token (if any).
            System.out.printf("Web server (HTTP/WebSocket) running at http://%s:8080/\n", cfg.host());
            System.out.printf("WebSocket endpoint at ws://%s:8081/wschat\n", cfg.host());
            System.out.printf("Chat server running on %s:%d  token = %s\n",
                cfg.host(), cfg.port(), cfg.token() == null ? "<none>" : cfg.token());

            SerConfig.acceptLoop(cfg, hub, tcp);

            // Wait for user input to terminate the server.
            System.in.read();
        } catch (DeploymentException | IOException e) {
            System.err.println("Connection aborted.");
        }
    }
}
