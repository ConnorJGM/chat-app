package com.connorgillmead.chat.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the clients connected to the chat server.
 * This class is responsible for adding and removing clients,
 * as well as broadcasting messages to all connected clients.
 */
final class ChatServerHub {
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    void addClient(ClientHandler c) {
        clients.add(c);
    }

    void removeClient(ClientHandler c) {
        clients.remove(c);
    }

    void broadcast(ChatMessage msg) {
        clients.forEach(c -> c.send(msg));
    }
}
