package com.connorgillmead.chat.server;

import java.io.*;
import java.net.Socket;

/**
 * Handles communication with a single client.
 * This class is responsible for reading messages from the client,
 * processing them, and sending responses back to the client.
 * It implements the Runnable interface to allow it to be run in a separate thread.
 */
final class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServerHub hub;
    private PrintWriter out;

    ClientHandler(Socket socket, ChatServerHub hub) {
        this.socket = socket;
        this.hub = hub;
    }

    @Override public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

            out = new PrintWriter(socket.getOutputStream(), true);
            hub.addClient(this);

            String line;
            while ((line = in.readLine()) != null) {
                ChatMessage msg = ChatMessage.fromJson(line);
                hub.broadcast(msg);
            }
        } catch (IOException ignored) {
        } finally {
            hub.removeClient(this);
        }
    }

    void send(ChatMessage msg) {
        out.println(msg.toJson());
    }
}
