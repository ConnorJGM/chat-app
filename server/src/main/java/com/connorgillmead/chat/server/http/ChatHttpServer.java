// StatusHttpServer.java

package com.connorgillmead.chat.server.http;

import com.connorgillmead.chat.server.http.api.ApiServer;
import com.connorgillmead.chat.server.http.ui.WebPageServer;
import com.connorgillmead.chat.server.tcp.ChatServerHub;
import com.connorgillmead.chat.server.tcp.SerConfig;
import com.connorgillmead.chat.server.websocket.WebSocketChatEndpoint;
import java.io.IOException;
import org.glassfish.grizzly.http.server.HttpServer;

/**
 * A simple HTTP server that provides status information about the chat server.
 * It listens on port 8080 and provides two endpoints:
 * 1. `/` - Displays the uptime and user count.
 * 2. `/users` - Displays a list of connected users in HTML format.
 */
public final class ChatHttpServer {
    /**
     * HTTP status bad request code.
     * This status code indicates that the server cannot process the request due to
     * client error (e.g., malformed request syntax).
     */
    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    /**
     * HTTP forbidden code.
     * This status code indicates that the server understands the request but refuses
     * to authorise it.
     */
    public static final int HTTP_FORBIDDEN = 403;
    /**
     * HTTP redirect code.
     * This status code indicates that the requested resource has been
     * temporarily moved to a different URI, and the client should use the new URI
     * for future requests.
     */
    public static final int HTTP_REDIRECT = 303;
    /**
     * HTTP method not allowed code.
     * This status code indicates that the request method is not supported for the
     * requested resource.
     */
    public static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int PORT_HTTP = 8080;

    /**
     * Private constructor to prevent instantiation.
     * This class is a utility class and should not be instantiated.
     */
    private ChatHttpServer() { }

    /**
     * Starts the HTTP server with the specified ChatServerHub.
     * This method initializes the HTTP server, registers handlers for various
     * endpoints,
     * and starts the server to listen for incoming requests.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return The started HttpServer instance.
     * @throws IOException If an I/O error occurs while starting the server.
     */
    public static HttpServer start(ChatServerHub hub, SerConfig cfg) throws IOException {
        return start(hub, cfg, new String[0]);
    }

    /**
     * Starts the HTTP server with the specified ChatServerHub.
     * This method initializes the HTTP server, registers handlers for various
     * endpoints,
     * and starts the server to listen for incoming requests.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return The started HttpServer instance.
     * @throws IOException If an I/O error occurs while starting the server.
     */
    public static HttpServer start(ChatServerHub hub, SerConfig cfg, String[] originalArgs) throws IOException {
        HttpServer server = HttpServer.createSimpleServer(null, PORT_HTTP);
        long startMillis = System.currentTimeMillis();

        // Register the HTTP handlers for the server.
        // These handlers will respond to HTTP requests for the root endpoint and the
        // users endpoint.
        // The startMillis is used to calculate the server's uptime.
        WebPageServer.register(server, hub, startMillis);
        ApiServer.register(server, hub, cfg, startMillis, originalArgs);
        // Attach the WebSocket endpoint to the hub.
        // This allows the WebSocket endpoint to access the ChatServerHub instance for
        // broadcasting messages.
        WebSocketChatEndpoint.attachHub(hub);

        // Start the HTTP server.
        server.start();
        return server;
    }
}
