package com.connorgillmead.chat.client;

import java.io.IOException;
import java.io.InputStream;
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
     
        try (ChatClient client = new ChatClient(host, port)) {
            Socket socket = client.socket();
            System.out.printf("Connected to %s:%d%n", host, port);

            /*
             * Thread B – relay server → stdout
             * This thread reads from the server's input stream and writes to the standard output stream.
             */
            new Thread(() -> {
                try (InputStream in = socket.getInputStream()) {
                    in.transferTo(System.out);   // copies until socket closes
                } catch (IOException e) {
                    System.out.println("Disconnected from server");
                }
            }).start();

            /*
             * Thread A – stdin → server
             * This thread reads from the standard input stream (keyboard) and writes to the server's output stream.
             */
            var out = socket.getOutputStream();

            /*
             * Scanner is used to read lines from the standard input stream (keyboard).
             * The try-with-resources statement ensures that the Scanner is closed when done.
             */
            try (Scanner kb = new Scanner(System.in)) {       // auto‑closes on exit
                while (kb.hasNextLine()) {
                    out.write((kb.nextLine() + '\n').getBytes());
                    out.flush();
                }
            }
        }
    }
}

