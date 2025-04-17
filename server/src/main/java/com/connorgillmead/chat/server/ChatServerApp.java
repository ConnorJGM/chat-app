package com.connorgillmead.chat.server;

import java.io.IOException;
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
            System.out.println("Server listening on " + port);

            /*
             * Thread A – accept connections
             * This thread blocks until a client connects to the server.
             */
            while (true) {
                Socket socket = tcp.awaitConnection();
                System.out.println("Client connected " + socket);

                // TODO replace echo with a real ClientHandler
                new Thread(() -> echoLoop(socket)).start();
            }
        }
    }

    /*
     * Thread B – echo server
     * This method reads from the client's input stream and writes to the client's output stream.
     */
    private static void echoLoop(Socket socket) {
        try (socket) {
            socket.getInputStream().transferTo(socket.getOutputStream());
        } catch (IOException ignored) {
        }
    }
}
