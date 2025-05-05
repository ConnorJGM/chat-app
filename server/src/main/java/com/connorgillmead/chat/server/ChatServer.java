// ChatServer.java

package com.connorgillmead.chat.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * ChatServer is a simple server that listens for incoming connections on a specified port.
 * It is designed to be used with a try-with-resources statement to ensure that the server socket is closed properly.
 */
public final class ChatServer implements AutoCloseable {

    // Constant keystore password for usability.
    private static final String KS_PASSWORD = "changeit";

    // The server socket used to accept client connections.
    private final ServerSocket serverSocket;

    /**
     * Creates a new ChatServer instance that listens on the specified port.
     * This constructor loads the keystore from a file and initialises the SSL context.
     *
     * @param port The port number on which the server will listen for incoming connections.
     * @throws IOException If an I/O error occurs when loading the keystore or creating the server socket.
     * @throws GeneralSecurityException If a security error occurs when loading the keystore
     *                                  or initialising the SSL context.
     *        This can happen if the keystore file is not found, the password is incorrect,
     *        or the keystore is not in the expected format.
     */
    public ChatServer(int port, String host) throws IOException, GeneralSecurityException {

        // Load the keystore from the specified file.
        // The keystore contains the server's private key and certificate chain.
        // The keystore is used to establish a secure connection with the client.
        // The keystore password is "changeit" (this should be changed in a production environment).
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = ChatServer.class.getResourceAsStream("/keystore.p12")) {
            ks.load(in, KS_PASSWORD.toCharArray());
        }

        // Create a KeyManagerFactory to manage the keys in the keystore.
        // The KeyManagerFactory is used to create key managers that manage the keys in the keystore.
        // The KeyManagerFactory is initialised with the keystore and the keystore password.
        // The KeyManagerFactory uses the default algorithm to create the key managers.
        KeyManagerFactory kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KS_PASSWORD.toCharArray());

        // Create an SSLContext to manage the SSL/TLS protocol.
        // The SSLContext is initialised with the key managers created by the KeyManagerFactory.
        // The SSLContext is used to create SSL sockets for secure communication.
        // The SSLContext uses the TLS protocol for secure communication.
        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(kmf.getKeyManagers(), null, null);

        // Create an SSLServerSocketFactory to create SSL server sockets.
        // The SSLServerSocketFactory is used to create server sockets that support SSL/TLS.
        SSLServerSocketFactory ssf = sslCtx.getServerSocketFactory();

        // Bind to the specified host.
        InetAddress bindAddr = InetAddress.getByName(host);

        // Create a server socket that listens on the specified port.
        // The server socket is used to accept incoming connections from clients.
        this.serverSocket = ssf.createServerSocket(
            port,
            0,
            bindAddr
            );
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
