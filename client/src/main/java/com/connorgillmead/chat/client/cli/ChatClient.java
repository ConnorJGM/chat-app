// ChatClient.java

package com.connorgillmead.chat.client.cli;

import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * ChatClient is a simple client that connects to a chat server.
 * It is designed to be used with a try-with-resources statement to ensure that the socket is closed properly.
 */
public final class ChatClient implements AutoCloseable {

    // The socket used to communicate with the server.
    private final Socket socket;

    /**
     * Creates a new ChatClient instance that connects to the specified server.
     * This constructor initializes the SSL context and creates a socket to connect to the server.
     *
     * @param host The hostname or IP address of the server to connect to.
     * @param port The port number on which the server is listening for connections.
     * @throws IOException If an I/O error occurs when creating the socket.
     * @throws GeneralSecurityException If a security error occurs when initializing the SSL context.
     *         This can happen if the SSL/TLS protocol is not supported or if the trust manager cannot be initialised.
     */
    public ChatClient(String host, int port) throws IOException, GeneralSecurityException {

        // Create an SSLContext to manage the SSL/TLS protocol.
        // The SSLContext is initialised with a TrustManager that trusts all certificates.
        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null,
            new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}

                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}

                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }

            }
            }, null);

        // Create an SSLSocketFactory from the SSLContext.
        // The SSLSocketFactory is used to create SSL sockets for secure communication.
        SSLSocketFactory ssf = sslCtx.getSocketFactory();
        this.socket = ssf.createSocket(host, port);
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
