package com.connorgillmead.chat.client;

import java.io.IOException;
import java.net.Socket;

/**
 * ChatClient is a simple client that connects to a chat server.
 * It is designed to be used with a try-with-resources statement to ensure that the socket is closed properly.
 */
public final class ChatClient implements AutoCloseable {

    // The socket used to communicate with the server.
    private final Socket socket;

    /**
     * Opens a TCP connection to the specified host and port.
     *
     * @param host The hostname or IP address of the server to connect to.
     * @param port The port number on which the server is listening for connections.
     * @throws IOException If an I/O error occurs when creating the socket.
     */
    public ChatClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
    }

    /** Returns the socket used to communicate with the server. */
    public Socket socket() {
        return socket;
    }

    /**
     * Closes the socket and releases any associated resources.
     *
     * @throws IOException If an I/O error occurs when closing the socket.
     */
    @Override
    public void close() throws IOException {
        socket.close();
    }
}
