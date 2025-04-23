// ChatServer.java

package com.connorgillmead.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ChatServer is a simple server that listens for incoming connections on a specified port.
 * It is designed to be used with a try-with-resources statement to ensure that the server socket is closed properly.
 */
public final class ChatServer implements AutoCloseable {

    // The server socket used to accept client connections
    private final ServerSocket serverSocket;

    /**
     * Binds the server socket to the specified port.
     *
     * @param port The port number on which the server will listen for incoming connections.
     * @throws IOException If an I/O error occurs when creating the server socket.
     */
    public ChatServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    /**
     * Blocks until a client connects to the server.
     *
     * @return The socket representing the connection to the client.
     * @throws IOException If an I/O error occurs when accepting the connection.
     */
    public Socket awaitConnection() throws IOException {
        return serverSocket.accept();
    }

    /*
     * Closes the server socket and releases any associated resources.
     */
    @Override
    public void close() throws IOException {
        serverSocket.close();
    }

    /**
     * Returns the port number on which the server is listening.
     *
     * @return The port number of the server socket.
     */
    // This method is used in the test to verify that the server is bound to a port.
    public int getPort() {
        return serverSocket.getLocalPort();
    }
}
