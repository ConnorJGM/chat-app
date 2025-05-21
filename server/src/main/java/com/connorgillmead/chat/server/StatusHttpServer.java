// StatusHttpServer.java

package com.connorgillmead.chat.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * A simple HTTP server that provides status information about the chat server.
 * It listens on port 8080 and provides two endpoints:
 * 1. `/` - Displays the uptime and user count.
 * 2. `/users` - Displays a list of connected users in HTML format.
 */
final class StatusHttpServer {

    // The port number for the HTTP server.
    // This is a static final field, meaning it is a constant value that does not change.
    private static final int PORT_HTTP      = 8080;

    // The HTTP status code for OK (200).
    // This is a static final field, meaning it is a constant value that does not change.
    // It indicates that the request was successful and the server has returned the requested data.
    private static final int HTTP_STATUS_OK = 200;

    // Utility class constructor.
    // This constructor is private to prevent instantiation of the class.
    private StatusHttpServer() { }

    /**
     * Starts the HTTP server on port 8080.
     * @param hub The chat server hub that manages connected users.
     * @throws IOException If an I/O error occurs when creating the server or handling requests.
     */
    static void start(ChatServerHub hub) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(PORT_HTTP), 0);

        // The start time of the server in milliseconds.
        // This variable is used to calculate the uptime of the server.
        long startMillis = System.currentTimeMillis();

        // / – status page with uptime and user count.
        // This context handles requests to the root URL ("/") and provides a status page.
        // It calculates the uptime of the server by subtracting the start time from the current time.
        // The uptime is displayed in a human-readable format using the Duration class.
        // The user count is obtained from the ChatServerHub instance.
        // The status page is generated using a StringBuilder to construct the HTML response.
        http.createContext("/", new PageHandler(() -> {
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
                            <p class = "card-text"><strong>Connected Users:</strong> %d</p>
                            <a href = "/users" class = "btn btn-primary mt-3">View Connected Users</a>
                        </div>
                    </div>
                </div>
                """.formatted(uptime, userCount));
            html.append(htmlFooter());
            return html.toString();
        }, "text/html"));

        // /users – list of connected users in HTML format.
        // This context handles requests to the "/users" URL and provides a list of connected users in HTML format.
        // It uses a StringBuilder to construct the HTML response.
        // The list of usernames is obtained from the ChatServerHub instance.
        // Each username is added to an unordered list (<ul>) in the HTML response.
        http.createContext("/users", new PageHandler(() -> {
            StringBuilder html = new StringBuilder();
            html.append(htmlHeader("Connected Users"));
            html.append("""
                <div class = "d-flex flex-column justify-content-center align-items-center"
                style = "min-height: 60vh">
                    <h2>Connected Users</h2>
                        <table class = "table table-bordered w-100 text-center mx-auto mb-4">
                            <tbody>
                """);

            // Create a table to display the usernames.
            // The table has a maximum of 4 columns, and the usernames are displayed in rows.
            // The number of rows is calculated based on the total number of users and the maximum number of columns.
            // Each username is displayed in a table cell (<td>).
            // If there are no users, an empty cell is displayed.
            // The table is styled with Bootstrap classes for better appearance.
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
                                    + "style = \"min-height = 3rem;\">")
                            .append(usernames[userIndex++])
                            .append("</td>");
                    } else {
                        html.append("<td class = \"bg-light\" style = \"min-height: 3rem; \"></td>");
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
            return html.toString();
        }, "text/html"));

        // Set the executor to a cached thread pool.
        // This allows the server to handle multiple requests concurrently using a thread pool.
        // The cached thread pool creates new threads as needed
        // and reuses previously constructed threads when they are available.
        ExecutorService cached = Executors.newCachedThreadPool();
        http.setExecutor(cached);

        // Start the server on port 8080.
        // The server is started with a backlog of 0, which means it will use the default backlog size.
        http.start();
        System.out.println("Status page running at http://localhost:8080/");
    }

    /**
     * Handles HTTP requests for the chat server.
     * This class implements the HttpHandler interface and provides a method to handle HTTP exchanges.
     * Uses the send200 method to send a 200 OK response with the generated body.
     * The body is generated by a Supplier function, allowing for dynamic content generation.
     */
    static class PageHandler implements HttpHandler {
        private final Supplier<String> bodySupplier;
        private final String mime;

        // Constructor for the PageHandler class.
        // It takes a Supplier<String> to generate the response body and a String for the MIME type.
        PageHandler(Supplier<String> bodySupplier, String mime) {
            this.bodySupplier = bodySupplier;
            this.mime = mime;
        }

        // The handle method is called when an HTTP request is received.
        // It generates the response body using the bodySupplier and sends a 200 OK response.
        @Override
        public void handle(HttpExchange ex) throws IOException {
            send200(ex, bodySupplier.get(), mime);
        }
    }

    /*
     * Sends a 200 OK response with the given body and MIME type.
     * This method is used to send HTTP responses to the client.
     * It sets the response headers, including the Content-Type header,
     * and writes the response body to the output stream.
     */
    private static void send200(HttpExchange ex, String body, String mime)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", mime + "; charset=utf-8");
        ex.getResponseHeaders().add("Connection", "close");
        ex.sendResponseHeaders(HTTP_STATUS_OK, bytes.length);

        // Write the response body to the output stream.
        // The response body is the byte array created from the string.
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
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
                    }
                    body {
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                    }
                    main.container {
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
}
