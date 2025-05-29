package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

/**
 * ApiServer is a utility class that registers HTTP handlers for the chat server's
 * API endpoints.
 * It provides endpoints for sending messages, checking server status, and kicking
 * users.
 * This class is not meant to be instantiated.
 */
final class ApiServer {
    private ApiServer() { }

    /**
     * Registers the HTTP handlers for the chat server's API endpoints.
     * This method sets up the `/send`, `/status.json`, and `/kick` endpoints for
     * the HTTP server.
     * @param server The HttpServer instance to register handlers with.
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @param startMillis The start time of the server in milliseconds.
     */
    static void register(HttpServer server, ChatServerHub hub, long startMillis) {
        server.getServerConfiguration()
                .addHttpHandler(createSendHandler(hub), "/send");
        server.getServerConfiguration()
                .addHttpHandler(createStatusJsonHandler(hub, startMillis), "/status.json");
        server.getServerConfiguration()
                .addHttpHandler(createKickHandler(hub), "/kick");
    }

    /**
     * Creates an HTTP handler for the `/send` endpoint.
     * This handler processes POST requests to send chat messages.
     * It validates the request parameters and broadcasts the message to all
     * connected users.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/send` endpoint.
     */
    private static HttpHandler createSendHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if ("POST".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    String formData = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> parameters = HttpConfig.parseFormData(formData);

                    String user = parameters.get("user");
                    String body = parameters.get("body");

                    String suppliedToken = request.getParameter("token");
                    if (hub.getToken() != null && !hub.getToken().isBlank()
                            && !hub.getToken().equals(suppliedToken)) {
                        response.setStatus(ChatHttpServer.HTTP_FORBIDDEN);
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
                        response.setStatus(ChatHttpServer.HTTP_STATUS_BAD_REQUEST);
                    }
                } else {
                    response.setStatus(ChatHttpServer.HTTP_METHOD_NOT_ALLOWED);
                }
            }
        };
    }

    /**
     * Creates an HTTP handler for the `/status.json` endpoint.
     * This handler returns the server's status information in JSON format.
     * It includes uptime, user count, and a list of connected users.
     *
     * @param hub         The ChatServerHub instance that manages chat
     *                    functionality.
     * @param startMillis The start time of the server in milliseconds.
     * @return An HttpHandler that handles requests to the `/status.json` endpoint.
     */
    private static HttpHandler createStatusJsonHandler(ChatServerHub hub, long startMillis) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                long uptime = System.currentTimeMillis() - startMillis;
                String formatted = HttpConfig.formatDuration(Duration.ofMillis(uptime));
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
     * It validates the request parameters and removes the specified user from the
     * chat.
     *
     * @param hub The ChatServerHub instance that manages chat functionality.
     * @return An HttpHandler that handles requests to the `/kick` endpoint.
     */
    private static HttpHandler createKickHandler(ChatServerHub hub) {
        return new HttpHandler() {
            @Override
            public void service(Request request, Response response) throws IOException {
                if (!"POST".equalsIgnoreCase(String.valueOf(request.getMethod()))) {
                    response.setStatus(ChatHttpServer.HTTP_METHOD_NOT_ALLOWED);
                    return;
                }

                String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> parameters = HttpConfig.parseFormData(body);
                String user = parameters.get("user");

                if (user == null || user.isBlank()) {
                    response.setStatus(ChatHttpServer.HTTP_STATUS_BAD_REQUEST);
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
