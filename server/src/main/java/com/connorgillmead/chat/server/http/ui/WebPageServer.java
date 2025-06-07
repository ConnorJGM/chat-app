// WebPageServer.java

package com.connorgillmead.chat.server.http.ui;

import com.connorgillmead.chat.server.http.ChatHttpServer;
import com.connorgillmead.chat.server.http.HttpConfig;
import com.connorgillmead.chat.server.tcp.ChatServerHub;
import java.io.IOException;
import java.time.Duration;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 * WebPageServer is a utility class that registers HTTP handlers for the chat server.
 * It provides endpoints for displaying server status, connected users, and the chat interface.
 * This class is not meant to be instantiated.
 */
public final class WebPageServer {
    // Private constructor to prevent instantiation.
    // This class is a utility class and should not be instantiated.
    private WebPageServer() { }

    /**
     * Registers the HTTP handlers for the chat server.
     * This method sets up the root handler, users handler, and chat handler for the
     * HTTP server.
     *
     * @param server      The HttpServer instance to register handlers with.
     * @param hub         The ChatServerHub instance that manages chat functionality.
     * @param startMillis The start time of the server in milliseconds.
     */
    public static void register(HttpServer server, ChatServerHub hub, long startMillis) {
        server.getServerConfiguration()
                .addHttpHandler(createRootHandler(hub, startMillis), "/");
        server.getServerConfiguration()
                .addHttpHandler(createUsersHandler(hub), "/users");
        server.getServerConfiguration()
                .addHttpHandler(createChatHandler(hub), "/chat");
    }

    /**
     * Creates an HTTP handler for the root endpoint (`/`).
     * This handler generates an HTML page displaying the server's status, including
     * uptime and user count.
     *
     * @param hub         The ChatServerHub instance that manages chat
     *                    functionality.
     * @param startMillis The start time of the server in milliseconds.
     * @return An HttpHandler that handles requests to the root endpoint.
     */
    private static HttpHandler createRootHandler(ChatServerHub hub, long startMillis) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startMillis);
                String uptime = HttpConfig.formatDuration(duration);
                int userCount = hub.userCount();

                String header = HttpConfig.loadHtml("web-pages/header.html")
                        .replace("{{title}}", "Chat Server Status")
                        .replace("{{heading}}", "Chat Server Status");
                String dashboard = HttpConfig.loadHtml("web-pages/dashboard.html")
                        .replace("{{uptime}}", uptime)
                        .replace("{{userCount}}", String.valueOf(userCount));
                String footer = HttpConfig.loadHtml("web-pages/footer.html");

                StringBuilder html = new StringBuilder();
                html.append(header)
                        .append(dashboard)
                        .append(footer);

                response.setContentType("text/html; charset=utf-8");
                response.getWriter().write(html.toString());
            }
        };
    }

    /**
     * Creates an HTTP handler for the `/users` endpoint.
     * This handler generates an HTML page displaying the list of connected users in
     * a table format.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/users` endpoint.
     */
    private static HttpHandler createUsersHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                String header = HttpConfig.loadHtml("web-pages/header.html")
                        .replace("{{title}}", "Connected Users")
                        .replace("{{heading}}", "Connected Users");
                String users = HttpConfig.loadHtml("web-pages/users.html")
                        .replace("{{userList}}", String.join("</li><li>", hub.getUsernames()));
                String footer = HttpConfig.loadHtml("web-pages/footer.html");

                StringBuilder html = new StringBuilder();
                html.append(header)
                        .append(users)
                        .append(footer);
                response.setContentType("text/html; charset=utf-8");
                response.getWriter().write(html.toString());
            }
        };
    }

    /**
     * Creates an HTTP handler for the `/chat` endpoint.
     * This handler serves the HTML page for the chat interface.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/chat` endpoint.
     */
    private static HttpHandler createChatHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if ("GET".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    String header = HttpConfig.loadHtml("web-pages/header.html")
                            .replace("{{title}}", "Chat Room")
                            .replace("{{heading}}", "Chat Room");
                    String chat = HttpConfig.loadHtml("web-pages/chat.html")
                            .replace("{{token}}", hub.getToken() != null ? hub.getToken() : "")
                            .replace("{{username}}", request.getParameter("username") != null
                                    ? request.getParameter("username")
                                    : "Guest");
                    String footer = HttpConfig.loadHtml("web-pages/footer.html");

                    StringBuilder html = new StringBuilder();
                    html.append(header)
                            .append(chat)
                            .append(footer);
                    response.setContentType("text/html; charset=utf-8");
                    response.getWriter().write(html.toString());
                } else {
                    response.setStatus(ChatHttpServer.HTTP_METHOD_NOT_ALLOWED);
                }
            }
        };
    }
}
