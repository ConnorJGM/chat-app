package com.connorgillmead.chat;

import com.connorgillmead.chat.server.ChatMessage;
import com.connorgillmead.chat.server.ChatServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for simple App.
 */
class AppTest {
    /**
     * Rigorous Test.
     */
    @Test
    void testApp() {
        assertEquals(1, 1);
    }

    /**
     * Test the server binds and accepts a connection.
     * @throws Exception if an error occurs during the test
     */
    @Test
    void serverBindsAndAccepts() throws Exception {
        try (ChatServer tcp = new ChatServer(0)) {
            int boundPort = tcp.getPort();
            assertTrue(boundPort > 0, "Server should be assigned by OS.");
        }
    }

    /**
     * Test the server binds and accepts a connection on a specific port.
     * @throws Exception if an error occurs during the test
     */
    @Test
    void jsonRoundTrip() {
        ChatMessage m1 = ChatMessage.of("Alice", "Hello, world!");
        String json = m1.toJson();
        ChatMessage m2 = ChatMessage.fromJson(json);
        assertEquals(m1.getUser(), m2.getUser(), "Sender should be the same.");
        assertEquals(m1.getBody(), m2.getBody(), "Message should be the same.");
    }
}
