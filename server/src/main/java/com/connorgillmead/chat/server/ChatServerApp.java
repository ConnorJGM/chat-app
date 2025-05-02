// ChatServerHub.java

package com.connorgillmead.chat.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Starts the TCP listener and spins up a thread for each client.
 */
public final class ChatServerApp {

    /**
     * Default port number for the chat server.
     * This is used if no port number is provided as a command-line argument.
     */
    private static final int DEFAULT_PORT = 5555;

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
     */
    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        try (ChatServer tcp = new ChatServer(port)) {
            System.out.println("Server listening on port:" + port);

            // Create a new ChatServerHub instance to manage connected clients.
            // The ChatServerHub is responsible for broadcasting messages to all connected clients.
            ChatServerHub hub = new ChatServerHub();

            // Start the HTTP server for status monitoring.
            // The StatusHttpServer provides a simple HTTP interface to check the server status and connected users.
            StatusHttpServer.start(hub);

            /*
             * Thread A – accept connections.
             * This thread accepts incoming connections from clients and starts a new thread for each client.
             * It reads the first line of input from the client to get the username,
             * and then creates a new ClientHandler thread to handle the client.
             */
            while (true) {
                Socket socket = tcp.awaitConnection();
                System.out.println("Client connected on " + socket);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                String firstLine = in.readLine();
                if (firstLine == null) {
                    socket.close();
                    continue;
                }

                // Parse the first line to get the username.
                // The first line is expected to be a JSON string representing a ChatMessage.
                ChatMessage hello = ChatMessage.fromJson(firstLine);

                // Get the username from the hello message.
                // The username is extracted from the ChatMessage object.
                String username = hello.getUser();

                // Add the client to the hub and broadcast the hello message.
                hub.broadcast(hello);

                // Thread B – handle client
                // This thread handles the client connection and processes messages.
                new Thread(new ClientHandler(socket, hub, username)).start();
            }
        }
    }
}
