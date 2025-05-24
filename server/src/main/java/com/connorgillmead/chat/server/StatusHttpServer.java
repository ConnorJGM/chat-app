// StatusHttpServer.java

package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
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

    private StatusHttpServer() { }

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

                StringBuilder html = new StringBuilder();
                html.append(htmlHeader("Chat Server Status"));
                html.append("""
                    <div class = "d-flex flex-column justify-center align-items-center"
                    style = "min-height: 60vh">
                        <div class = "card mb-4 w-75 mx-auto" style = "min-height: 60vh;">
                            <div class = "card-body d-flex flex-column justify-content-center align-items-center"
                            style = "height: 80%%;">
                                <h2 class = "card-title">Dashboard</h2>
                                <p class = "card-text"><strong>Uptime: </strong> %s</p>
                                <p class = "card-text"><strong>Connected Users: </strong> %d</p>
                                <a href = "/users" class = "btn btn-primary mt-3">View Connected Users</a>
                            </div>
                        </div>
                    </div>
                    """.formatted(uptime, userCount));
                html.append(htmlFooter());

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
                StringBuilder html = new StringBuilder();
                html.append(htmlHeader("Connected Users"));
                html.append("""
                    <div class = "d-flex flex-column justify-content-center align-items-center"
                    style = "min-height: 60vh">
                        <h2>Connected Users</h2>
                            <table class = "table table-bordered w-100 text-center mx-auto mb-4">
                                <tbody>
                    """);

                final int maxCol = 4;
                var usernames = hub.getUsernames().toArray(new String[0]);
                int totalUsers = usernames.length;
                int numRows = Math.max(2, (int) Math.ceil(totalUsers / (double) maxCol));
                int userIndex = 0;

                for (int row = 0; row < numRows; row++) {
                    html.append("<tr>");
                    for (int col = 0; col < maxCol; col++) {
                        if (userIndex < totalUsers) {
                            html.append("<td class = \"text-center align-middle bg-info text-dark fw-bold\" "
                                        + "style = \"min-height: 3rem;\">")
                                .append(usernames[userIndex++])
                                .append("</td>");
                        } else {
                            html.append("<td class = \"bg-light\" style = \"min-height: 3rem;\"></td>");
                        }
                    }
                    html.append("</tr>");
                }

                html.append("""
                                    </tbody>
                                </table>
                                <a href = "/" class = "btn btn-secondary">Back to Status Page</a>
                            </div>
                    """);
                html.append(htmlFooter());

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
                    String html = getChatHtml();
                    response.setContentType("text/html; charset=utf-8");
                    response.setContentLength(html.getBytes(StandardCharsets.UTF_8).length);
                    response.getWriter().write(html);
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
                    Map<String, String> params = parseFormData(formData);

                    String user = params.get("user");
                    String body = params.get("body");

                    String suppliedToken = request.getParameter("token");
                    if (hub.getToken() != null && !hub.getToken().isBlank()
                        && !hub.getToken().equals(suppliedToken)) {
                        response.setStatus(HTTP_FORBIDDEN);
                        return;
                    }

                    if (user != null && body != null && !user.isBlank() && !body.isBlank()) {
                        ChatMessage msg = ChatMessage.of(user, body);
                        hub.broadcast(msg);

                        String resp = "Message from " + user + " sent: " + body;
                        response.setContentType("text/plain; charset=utf-8");
                        response.setContentLength(resp.getBytes(StandardCharsets.UTF_8).length);
                        response.getWriter().write(resp);
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

        // Attach the WebSocket endpoint to the hub.
        // This allows the WebSocket endpoint to access the ChatServerHub instance for broadcasting messages.
        WebSocketChatEndpoint.attachHub(hub);

        // Start the HTTP server.
        server.start();
        return server;
    }

    /**
     * Generates the HTML header for the response.
     * This method creates the HTML header with the specified title.
     * It includes the necessary meta tags and links to Bootstrap CSS for styling.
     * @param title The title of the HTML page.
     * @return The HTML header as a string.
     */
    private static String htmlHeader(String title) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>%s</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css"
                rel="stylesheet">
                <style>
                    html, body {
                        height: 100%%;
                        margin: 0;
                        padding: 0;
                    }
                    body {
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                    }
                    main.container {
                        width: 80%%;
                        max-width: 80%%;
                        margin: 0 auto;
                        flex: 1 0 auto;
                    }
                    footer {
                        flex-shrink: 0;
                    }
                </style>
            </head>
            <body>
                <header class="bg-primary text-white text-center py-3 mb-4">
                    <h1>Chat Server Status</h1>
                </header>
                <main class="container">
            """, title);
    }

    /**
     * Generates the HTML footer for the response.
     * This method creates the HTML footer with a copyright notice.
     * @return The HTML footer as a string.
     */
    private static String htmlFooter() {
        return """
                </main>
                <footer class = "bg-light text-center py-3 mt-4 border-top">
                    <small>&copy; Connor Gill-Mead 2025</small>
                </footer>
            </body>
            </html>
            """;
    }

    /**
     * Formats a Duration object into a human-readable string.
     * This method takes a Duration object and converts it into a string representation
     * @param d The Duration object to format.
     * @return A string representation of the duration in a human-readable format.
     */
    private static String formatDuration(Duration d) {
        final int totalHours = 24;
        final int totalOther = 60;

        long days = d.toDays();
        long hours = d.toHours() % totalHours;
        long minutes = d.toMinutes() % totalOther;
        long seconds = d.getSeconds() % totalOther;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" Days ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append(" Hours ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append(" Minutes ");
        }
        sb.append(seconds).append(" Seconds");
        return sb.toString();
    }

    /**
     * Generates the HTML for the chat page.
     * This method creates the HTML structure for the chat page, including the chat log and input fields.
     * @return The HTML for the chat page as a string.
     */
    private static String getChatHtml() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Web Chat</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css"
                rel="stylesheet">
                <style>
                    #chatlog {
                        border: 1px solid #ccc;
                        height: 300px;
                        overflow: auto;
                        margin-bottom: 1em;
                        padding: 0.5em;
                        background: #f8f9fa;
                    }
                </style>
            </head>
            <body class="bg-light">
                <div class="container py-4">
                    <div class="row gx-2">
                        <div class="col-md-8">
                            <div class="card shadow mb-3">
                                <div class="card-body">
                                    <h2 class="card-title text-center mb-4">Web Chat</h2>
                                    <div id="chatlog" class="mb-3"></div>
                                    <form id="connectForm" class="row g-2 mb-3" onsubmit="connect(); return false;">
                                        <div class="col-5">
                                            <input type="text" id="user" class="form-control"
                                            placeholder="Username" required>
                                        </div>
                                        <div class="col-5">
                                            <input type="text" id="token" class="form-control"
                                            placeholder="Token (if required)">
                                        </div>
                                        <div class="col-2 d-grid">
                                            <button type="submit" class="btn btn-primary"
                                            id="connectBtn">Connect</button>
                                        </div>
                                    </form>
                                    <form id="chatArea" class="row g-2" style="display:none;"
                                    onsubmit="sendMsg(); return false;">
                                        <div class="col-10">
                                            <input type="text" id="body" class="form-control"
                                            placeholder="Type a message..." required autocomplete="off">
                                        </div>
                                        <div class="col-2 d-grid">
                                            <button type="submit" class="btn btn-success">Send</button>
                                        </div>
                                    </form>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-4">
                            <div class="card shadow">
                                <div class="card-body">
                                    <h5 class="card-title">Connected Users</h5>
                                    <ul id="userList" class="list-group"></ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <script>
                let ws, user, token;
                function connect() {
                    user = document.getElementById("user").value;
                    token = document.getElementById("token").value;
                    ws = new WebSocket("ws://" + location.hostname + ":8081/wschat");
                    ws.onopen = function() {
                        ws.send(JSON.stringify({type: "hello", user: user, token: token}));
                        document.getElementById("chatArea").style.display = "";
                        document.getElementById("connectBtn").disabled = true;
                        document.getElementById("user").readOnly = true;
                        document.getElementById("token").readOnly = true;
                    };
                    ws.onmessage = function(event) {
                        const m = JSON.parse(event.data);
                        switch(m.type) {
                            case "roster":
                                const ul = document.getElementById("userList");
                                ul.innerHTML = "";
                                (m.users||[]).forEach(u => {
                                    const li = document.createElement("li");
                                    li.textContent = u;
                                    li.className = "list-group-item";
                                    ul.appendChild(li);
                                });
                                break;
                             case "text":
                                const log = document.getElementById("chatlog");
                                const div = document.createElement("div");
                                div.textContent = `${m.user}: ${m.body}`;
                                log.appendChild(div);
                                log.scrollTop = log.scrollHeight;
                                break;
                            }
                        };
                        ws.onclose = function() {
                            document.getElementById("chatArea").style.display = "none";
                            document.getElementById("connectBtn").disabled = false;
                            document.getElementById("user").readOnly = false;
                            document.getElementById("token").readOnly = false;
                        };
                    }

                function sendMsg() {
                    let body = document.getElementById("body").value;
                    if (!body || ws.readyState !== WebSocket.OPEN) return;
                    ws.send(JSON.stringify({type: "text", user: user, body: body}));
                    document.getElementById("body").value = "";
                }
                </script>
            </body>
            </html>
            """;
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
     * WebSocket endpoint for the chat server.
     * This class handles WebSocket connections and messages.
     * It uses the Jakarta WebSocket API to manage WebSocket sessions.
     */
    @ServerEndpoint("/wschat")
    public static class WebSocketChatEndpoint {
        private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
        private static final Map<Session, WebHandler> HANDLER_MAP = new ConcurrentHashMap<>();
        private static ChatServerHub hub;

        /**
         * Handles the opening of a WebSocket connection.
         * This method is called when a new WebSocket connection is established.
         * @param session The WebSocket session that was opened.
         */
        @OnOpen
        public void onOpen(Session session) {
            System.out.println("WebSocket: Connection opened: " + session.getId());
            SESSIONS.add(session);
            WebHandler handler = new WebHandler(session, hub, null);
            HANDLER_MAP.put(session, handler);
        }

        /**
         * Handles incoming messages from the WebSocket client.
         * This method is called when a message is received from the client.
         * @param json The JSON string representing the ChatMessage.
         * @param session The WebSocket session that sent the message.
         */
        @OnMessage
        public void onMessage(String json, Session session) throws IOException {
            ChatMessage msg = ChatMessage.fromJson(json);
            WebHandler handler = HANDLER_MAP.get(session);

            // Check if the message is a "hello" message.
            // If the handler is not authenticated, check if the message is a "hello" message.
            if (handler != null && !handler.isAuthenticated()) {
                if (!"hello".equals(msg.getType())) {
                    System.out.println("WebSocket: First message was not 'hello', closing.");
                    session.close();
                    return;
                }

                // Required token validation.
                // If the server requires a token, check if the provided token matches the required token.
                String requiredToken = hub.getToken();
                if (requiredToken != null && !requiredToken.isBlank() && !requiredToken.equals(msg.getToken())) {
                    System.out.println("WebSocket: Invalid or missing token for user: " + msg.getUser());
                    session.close();
                    return;
                }


                // Check if the username is already in use.
                // If the username is already in use, close the session.
                if (!hub.reserveName(msg.getUser())) {
                    System.out.println("WebSocket: Username already in use: " + msg.getUser());
                    session.close();
                    return;
                }

                // Set the username and authenticated status for the handler.
                handler.setUsername(msg.getUser());
                handler.setAuthenticated(true);
                hub.getHistory().forEach(handler::send);

                // Broadcast the "hello" message to all connected clients.
                ChatMessage hello = ChatMessage.hello(msg.getUser(), null);
                hub.broadcast(hello);
                hub.broadcast(ChatMessage.userList(hub.getUsernames()));
                return;
            }

            // If the handler is authenticated, process the message.
            // If the message is a command, handle it accordingly.
            if (handler != null && handler.isAuthenticated()) {
                if (CommandHelper.command(msg, hub, handler)) {
                    return;
                }
                hub.broadcast(msg);
            }
        }

        /**
         * Handles the closing of a WebSocket connection.
         * This method is called when the WebSocket connection is closed.
         * @param session The WebSocket session that was closed.
         */
        @OnClose
        public void onClose(Session session) {
            SESSIONS.remove(session);
            WebHandler handler = HANDLER_MAP.remove(session);
            if (handler != null) {
                hub.releaseName(handler.getUsername());
                hub.removeClient(handler);
                hub.broadcast(ChatMessage.userList(hub.getUsernames()));
            }
        }

        /**
         * Handles errors that occur during WebSocket communication.
         * This method is called when an error occurs in the WebSocket connection.
         * @param t The Throwable object representing the error.
         */
        @OnError
        public void onError(Throwable t) {
            t.printStackTrace();
        }

        /**
         * Sends a message to all connected WebSocket clients.
         * @param msg The ChatMessage to send.
         */
        public static void sendToAll(ChatMessage msg) {
            String json = msg.toJson();
            for (Session s : SESSIONS) {
                if (s.isOpen()) {
                    try {
                        s.getBasicRemote().sendText(json);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * Attaches the ChatServerHub instance to the WebSocket endpoint.
         * This method is called to set the ChatServerHub instance for the WebSocket endpoint.
         * @param h The ChatServerHub instance to attach.
         */
        public static void attachHub(ChatServerHub h) {
            hub = h;
        }
    }
}
