// ChatClientApp.java

package com.connorgillmead.chat.client;

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
        String host = (args.length > 0) ? args[0] : "localhost";
        int    port = (args.length > 1) ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        // Create a new ChatClient instance and connect to the server.
        // The try-with-resources statement ensures that the socket is closed properly when done.
        try (ChatClient client = new ChatClient(host, port);
             Scanner    kb     = new Scanner(System.in, "UTF-8")) {

            Socket socket = client.socket();
            System.out.printf("Connected to %s:%d%n", host, port);

            System.out.print("Username: ");
            String me = kb.nextLine();

            /**
             * Thread B – receive messages
             * This thread reads messages from the server and prints them to the console.
             * It runs in a separate thread to allow for concurrent message sending and receiving.
             * The thread will continue to run until the socket is closed or an I/O error occurs.
             */
            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        ChatMessage m = ChatMessage.fromJson(line);
                        System.out.printf("%s: %s%n", m.getUser(), m.getBody());
                    }
                } catch (IOException ignored) { }
            }).start();

            // Thread A – send messages
            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"),
                 true
                );

            // This thread reads user input from the console and sends it to the server.
            // It runs in a loop until the user enters "exit" or an I/O error occurs.
            while (kb.hasNextLine()) {
                String text = kb.nextLine();
                ChatMessage msg = ChatMessage.of(me, text);
                out.println(msg.toJson());
            }
        }
    }
}

