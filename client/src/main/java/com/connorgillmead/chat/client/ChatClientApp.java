// ChatClientApp.java

package com.connorgillmead.chat.client;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * ChatClientApp is a simple command-line chat client that connects to a chat server.
 * The client runs in two threads: one for sending messages and another for receiving messages.
 */
public final class ChatClientApp {

    /**
     * Default port number for the chat server.
     * This is used if no port number is provided as a command-line argument.
     */
    private static final int DEFAULT_PORT = 5555;

    /**
     * Private constructor to prevent instantiation.
     * This class is not meant to be instantiated; it only contains a main method.
     */
    private ChatClientApp() {
    }

    /**
     * Main method to start the client.
     * It connects to the server and starts two threads: one for sending messages and another for receiving messages.
     * @param args The first argument is the hostname, and the second argument is the port number.
     * @throws IOException If an I/O error occurs when creating the socket or transferring data.
     */
    public static void main(String[] args) throws IOException {

        try (Scanner console = new Scanner(System.in, "UTF-8")) {

            // Default host and port values.
            // If no arguments are provided, the user is prompted for the host and port.
            String host;
            int    port;

            // If no command-line arguments are provided, prompt the user for the host and port.
            // The user can press Enter to use the default values.
            if (args.length == 0) {
                System.out.print("Server host [localhost]: ");
                host = console.nextLine().trim();
                if (host.isEmpty()) {
                    host = "localhost";
                }

                // If the user does not provide a host, use "localhost" as the default.
                while (true) {
                    System.out.print("Server port [5555]: ");
                    String p = console.nextLine().trim();
                    if (p.isEmpty()) {
                        port = DEFAULT_PORT;
                        break;
                    }
                    // If the user provides a port, parse it as an integer.
                    // If the port is invalid, prompt the user to enter a valid number.
                    try {
                        port = Integer.parseInt(p);
                        break;
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid port number. Please enter a valid number.");
                    }
                }
            // If the user provides a host and port as command-line arguments, use them.
            // The first argument is the host, and the second argument is the port.
            } else if (args.length == 1) {
                host = args[0];
                port = DEFAULT_PORT;
            } else if (args.length == 2) {
                host = args[0];
                port = Integer.parseInt(args[1]);
            } else {
                System.out.println("Usage: java -jar chat-client.jar [host] [port]");
                return;
            }

            // Create a new ChatClient instance and connect to the server.
            // The try-with-resources statement ensures that the socket is closed properly when done.
            try (ChatClient client = new ChatClient(host, port)) {

                Socket socket = client.socket();
                System.out.printf("Connected to %s:%d%n", host, port);
                startReader(socket);

                // Prompt the user for their username.
                // The username is used to identify the user in the chat.
                System.out.print("Username: ");
                String me = console.nextLine();

                // Thread A – send messages.
                // This thread sends messages to the server.
                // It uses a PrintWriter to send text data to the server.
                PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"),
                    true
                    );

                // Notify the server that the user has joined the chat.
                out.println(ChatMessage.of(me, "joined the chat.").toJson());

                // This thread reads user input from the console and sends it to the server.
                // It runs in a loop until the user enters "exit" or an I/O error occurs.
                while (console.hasNextLine()) {
                    String text = console.nextLine();

                    // If the user enters "exit", notify the server and break the loop.
                    // This allows the user to leave the chat gracefully.
                    if ("quit".equalsIgnoreCase(text.trim())) {
                        break;
                    }

                    // Messages are sent to the server in JSON format.
                    // The ChatMessage class is used to create a message object with the username and message body.
                    ChatMessage msg = ChatMessage.of(me, text);
                    out.println(msg.toJson());
                }
            }
        }
    }

    /**
    * Thread B – receive messages.
    * This thread reads messages from the server and prints them to the console.
    * It runs in a separate thread to allow for concurrent message sending and receiving.
    * The thread will continue to run until the socket is closed or an I/O error occurs.
    */
    private static void startReader(Socket socket) {

        // Create a new thread to read messages from the server.
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                String line;

                // Read messages from the server in a loop.
                // Each message is expected to be in JSON format.
                while ((line = in.readLine()) != null) {
                    ChatMessage m = ChatMessage.fromJson(line);
                    System.out.printf("%s: %s%n", m.getUser(), m.getBody());
                }
            } catch (IOException ignored) {
            }
        }).start();
    }
}
