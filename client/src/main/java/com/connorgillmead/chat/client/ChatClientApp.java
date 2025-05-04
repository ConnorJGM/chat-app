// ChatClientApp.java

package com.connorgillmead.chat.client;

import com.connorgillmead.chat.common.ChatMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.GeneralSecurityException;
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
     * @throws GeneralSecurityException If a security error occurs when creating the socket.
     *         This can happen if the SSL/TLS protocol is not supported or if the trust manager cannot be initialised.
     */
    public static void main(String[] args) throws IOException, GeneralSecurityException {

        try (Scanner console = new Scanner(System.in, "UTF-8")) {

            // Default host and port values.
            // If no arguments are provided, the user is prompted for the host and port.
            String host = null;
            int    port = -1;
            String token = null;

            // Begin looping through command-line arguments.
            for (int i = 0; i < args.length;) {
                String a = args[i];

                // If current argument is "-t", check for token.
                if ("-t".equals(a) && i + 1 < args.length) {
                    token = args[i + 1];
                    i += 2;
                } else {
                    host = a;
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[i + 1]);
                        i += 2;
                    } else {
                        i += 1;
                    }
                }
            }

            // If no host argument is given, prompt user for host name.
            if (host == null) {
                System.out.print("Server host [localhost]: ");
                String h = console.nextLine().trim();
                if (h.isEmpty()) {
                    host = "localhost";
                }
            }

            // If no port number is given, prompt user for port number.
            if (port == -1) {
                System.out.print("Server port [" + DEFAULT_PORT + "]: ");
                String p = console.nextLine().trim();
                if (!p.isEmpty()) {
                    port = Integer.parseInt(p);
                } else {
                    port = DEFAULT_PORT;
                }
            }

            // If no token is given, prompt user for a token.
            if (token == null) {
                System.out.print("Access token (or Enter for none): ");
                String t = console.nextLine().trim();
                token = t.isEmpty() ? null : t;
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
                out.println(ChatMessage.hello(me, token).toJson());

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
        // Close the socket and release any associated resources.
        // The try-with-resources statement ensures that the socket is closed properly when done.
        // Catch any exceptions that occur during the process.
        // This includes GeneralSecurityException and IOException.
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
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
