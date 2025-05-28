// StatusHttpServer.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 * A simple HTTP server that provides status information about the chat server.
 * It listens on port 8080 and provides two endpoints:
 * 1. `/` - Displays the uptime and user count.
 * 2. `/users` - Displays a list of connected users in HTML format.
 */
final class StatusHttpServer {

    // Constants for HTTP server configuration.
    // These constants define the port number for the HTTP server and various HTTP status codes.
    private static final int PORT_HTTP = 8080;
    private static final int HTTP_STATUS_BAD_REQUEST = 400;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final int HTTP_FORBIDDEN = 403;

    /**
     * Private constructor to prevent instantiation.
     * This class is a utility class and should not be instantiated.
     */
    private StatusHttpServer() { }

    /**
     * Loads an HTML resource from the classpath.
     * This method reads the content of an HTML file from the resources directory and returns it as a string.
     * @param resourcePath The path to the HTML resource file.
     * @return The content of the HTML file as a string.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    private static String loadHtml(String resourcePath) throws IOException {
        try (InputStream input =
                StatusHttpServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new FileNotFoundException("Cannot find resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Creates an HTTP handler for the root endpoint (`/`).
     * This handler generates an HTML page displaying the server's status, including uptime and user count.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @param startMillis The start time of the server in milliseconds.
     * @return An HttpHandler that handles requests to the root endpoint.
     */
    private static HttpHandler createRootHandler(ChatServerHub hub, long startMillis) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                Duration duration = Duration.ofMillis(System.currentTimeMillis() - startMillis);
                String uptime = formatDuration(duration);
                int userCount = hub.userCount();

                String header = loadHtml("web-pages/header.html")
                    .replace("{{title}}", "Chat Server Status")
                    .replace("{{heading}}", "Chat Server Status");
                String dashboard = loadHtml("web-pages/dashboard.html")
                    .replace("{{uptime}}", uptime)
                    .replace("{{userCount}}", String.valueOf(userCount));
                String footer = loadHtml("web-pages/footer.html");

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
     * This handler generates an HTML page displaying the list of connected users in a table format.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/users` endpoint.
     */
    private static HttpHandler createUsersHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                String header = loadHtml("web-pages/header.html")
                    .replace("{{title}}", "Connected Users")
                    .replace("{{heading}}", "Connected Users");
                String users = loadHtml("web-pages/users.html")
                    .replace("{{userList}}", String.join("</li><li>", hub.getUsernames()));
                String footer = loadHtml("web-pages/footer.html");

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
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/chat` endpoint.
     */
    private static HttpHandler createChatHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if ("GET".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    String header = loadHtml("web-pages/header.html")
                        .replace("{{title}}", "Chat Room")
                        .replace("{{heading}}", "Chat Room");
                    String chat = loadHtml("web-pages/chat.html")
                        .replace("{{token}}", hub.getToken() != null ? hub.getToken() : "")
                        .replace("{{username}}", request.getParameter("username") != null
                            ? request.getParameter("username") : "Guest");
                    String footer = loadHtml("web-pages/footer.html");

                    StringBuilder html = new StringBuilder();
                    html.append(header)
                        .append(chat)
                        .append(footer);
                    response.setContentType("text/html; charset=utf-8");
                    response.getWriter().write(html.toString());
                } else {
                    response.setStatus(HTTP_METHOD_NOT_ALLOWED);
                }
            }
        };
    }

    /**
     * Creates an HTTP handler for the `/send` endpoint.
     * This handler processes POST requests to send chat messages.
     * It validates the request parameters and broadcasts the message to all connected users.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/send` endpoint.
     */
    private static HttpHandler createSendHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if ("POST".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    String formData = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> parameters = parseFormData(formData);

                    String user = parameters.get("user");
                    String body = parameters.get("body");

                    String suppliedToken = request.getParameter("token");
                    if (hub.getToken() != null && !hub.getToken().isBlank()
                        && !hub.getToken().equals(suppliedToken)) {
                        response.setStatus(HTTP_FORBIDDEN);
                        return;
                    }

                    if (user != null && body != null && !user.isBlank() && !body.isBlank()) {
                        ChatMessage chatMessage = ChatMessage.of(user, body);
                        hub.broadcast(chatMessage);

                        String sent = "Message from " + user + " sent: " + body;
                        response.setContentType("text/plain; charset=utf-8");
                        response.setContentLength(sent.getBytes(StandardCharsets.UTF_8).length);
                        response.getWriter().write(sent);
                    } else {
                        response.setStatus(HTTP_STATUS_BAD_REQUEST);
                    }
                } else {
                    response.setStatus(HTTP_METHOD_NOT_ALLOWED);
                }
            }
        };
    }

    /**
     * Starts the HTTP server with the specified ChatServerHub.
     * This method initializes the HTTP server, registers handlers for various endpoints,
     * and starts the server to listen for incoming requests.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return The started HttpServer instance.
     * @throws IOException If an I/O error occurs while starting the server.
    */
    static HttpServer start(ChatServerHub hub) throws IOException {
        HttpServer server = HttpServer.createSimpleServer(null, PORT_HTTP);
        long startMillis = System.currentTimeMillis();

        // Register HTTP handlers for various endpoints.
        // The root handler serves the status page, while the other handlers serve specific functionalities.
        server.getServerConfiguration().addHttpHandler(createRootHandler(hub, startMillis), "/");
        server.getServerConfiguration().addHttpHandler(createUsersHandler(hub), "/users");
        server.getServerConfiguration().addHttpHandler(createChatHandler(hub), "/chat");
        server.getServerConfiguration().addHttpHandler(createSendHandler(hub), "/send");
        server.getServerConfiguration().addHttpHandler(createStatusJsonHandler(hub, startMillis), "/status.json");
        server.getServerConfiguration().addHttpHandler(createKickHandler(hub), "/kick");

        // Attach the WebSocket endpoint to the hub.
        // This allows the WebSocket endpoint to access the ChatServerHub instance for broadcasting messages.
        WebSocketChatEndpoint.attachHub(hub);

        // Start the HTTP server.
        server.start();
        return server;
    }

    /**
     * Formats a Duration object into a human-readable string.
     * This method takes a Duration object and converts it into a string representation
     * @param duration The Duration object to format.
     * @return A string representation of the duration in a human-readable format.
     */
    private static String formatDuration(Duration duration) {
        // Constants for time units.
        final int totalHours = 24;
        final int totalOther = 60;

        // Calculate the number of days, hours, minutes, and seconds from the Duration object.
        long days = duration.toDays();
        long hours = duration.toHours() % totalHours;
        long minutes = duration.toMinutes() % totalOther;
        long seconds = duration.getSeconds() % totalOther;
        StringBuilder stringBuilder = new StringBuilder();
        if (days > 0) {
            stringBuilder.append(days).append(" Days ");
        }
        if (hours > 0 || days > 0) {
            stringBuilder.append(hours).append(" Hours ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            stringBuilder.append(minutes).append(" Minutes ");
        }
        stringBuilder.append(seconds).append(" Seconds");
        return stringBuilder.toString();
    }

    /**
     * Parses form data from a URL-encoded string into a map of key-value pairs.
     * This method is used to extract parameters from the form data submitted via HTTP POST.
     * @param formData The URL-encoded form data as a string.
     * @return A map containing the parsed key-value pairs.
     */
    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        for (String pair : formData.split("&")) {
            String[] kv = pair.split("=", 2); if (kv.length == 2) {
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                map.put(key, val);
            }
        } return map;
    }

    /**
     * Creates an HTTP handler for the `/status.json` endpoint.
     * This handler returns the server's status information in JSON format.
     * It includes uptime, user count, and a list of connected users.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @param startMillis The start time of the server in milliseconds.
     * @return An HttpHandler that handles requests to the `/status.json` endpoint.
     */
    private static HttpHandler createStatusJsonHandler(ChatServerHub hub, long startMillis) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                long uptime = System.currentTimeMillis() - startMillis;
                String formatted = formatDuration(Duration.ofMillis(uptime));
                int users = hub.userCount();

                // Create a map to hold the status information.
                // This map will be converted to JSON format for the response.
                Map<String, Object> status = new HashMap<>();
                status.put("uptime", uptime);
                status.put("uptimeString", formatted);
                status.put("users", users);
                status.put("userList", hub.getUsernames());

                // Convert the status map to JSON format using Gson.
                // The JSON response will be sent to the client.
                String json = new com.google.gson.Gson().toJson(status);
                response.setContentType("application/json; charset=utf-8");
                response.getWriter().write(json);
            }
        };
    }

    /**
     * Creates an HTTP handler for the `/kick` endpoint.
     * This handler processes POST requests to kick a user from the chat server.
     * It validates the request parameters and removes the specified user from the chat.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/kick` endpoint.
     */
    private static HttpHandler createKickHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if (!"POST".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    response.setStatus(HTTP_METHOD_NOT_ALLOWED);
                    return;
                }

                String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> parameters = parseFormData(body);
                String user = parameters.get("user");

                if (user == null || user.isBlank()) {
                    response.setStatus(HTTP_STATUS_BAD_REQUEST);
                    response.getWriter().write("User parameter is required.");
                    return;
                }

                WebSocketChatEndpoint.getSessions().stream()
                    .filter(s -> {
                        WebHandler webHandler = WebSocketChatEndpoint.getHandlerMap().get(s);
                        return webHandler != null && user.equals(webHandler.getUsername());
                    })
                    .findFirst()
                    .ifPresent(s -> {
                        WebHandler webHandler = WebSocketChatEndpoint.getHandlerMap().get(s);
                        webHandler.send(ChatMessage.kick("You have been kicked from the chat server."));
                        hub.kickClient(user);
                        try {
                            s.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                hub.kickClient(user);

                response.setContentType("text/plain; charset=utf-8");
                String kicked = "Kicked: " + user;
                response.setContentLength(kicked.getBytes(StandardCharsets.UTF_8).length);
                response.getWriter().write(kicked);
            }
        };
    }
}
