// ChatClientNet.java

package com.connorgillmead.chat.client.cli;

import com.connorgillmead.chat.common.ChatMessage;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

/**
 * ChatClientNet is a utility class that handles network operations for the chat client.
 * It provides a method to read messages from the server and put them into a blocking queue.
 * This class is not meant to be instantiated.
 * It is a static utility class that provides a single method for reading messages.
 */
public final class ChatClientNet {

    // Private constructor to prevent instantiation.
    private ChatClientNet() {
    }

    /**
     * Read loop for the client.
     * This method runs in a separate thread and reads messages from the server.
     * It puts the messages into a blocking queue for processing.
     * The loop continues until the socket is closed or an error occurs.
     * @param socket The socket to read from.
     * @param queue The queue to put messages into.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws SocketException If the socket is closed.
     */
    public static void readLoop(Socket socket, BlockingQueue<ChatMessage> queue) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    ChatMessage m = ChatMessage.fromJson(line);
                    queue.put(m);
                } catch (JsonSyntaxException | IllegalStateException e) {
                    continue;
                }
            }
        } catch (SocketException e) {
        } catch (IOException | InterruptedException e) {
            System.err.println("NET error: " + e.getMessage());
        }
    }
}
