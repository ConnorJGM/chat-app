package com.connorgillmead.chat.server;

import com.connorgillmead.chat.common.ChatMessage;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket endpoint for the chat server.
 * This class handles WebSocket connections and messages.
 * It uses the Jakarta WebSocket API to manage WebSocket sessions.
*/
@ServerEndpoint("/wschat")
public class WebSocketChatEndpoint {
    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();
    private static final Map<Session, WebHandler> HANDLER_MAP = new ConcurrentHashMap<>();
    private static ChatServerHub hub;

    /**
     * Returns the set of WebSocket sessions.
     * This method is used to get the current WebSocket sessions.
     * @return The set of WebSocket sessions.
    */
    public static Set<Session> getSessions() {
        return SESSIONS;
    }

    /**
     * Returns the map of WebSocket sessions to WebHandler instances.
     * This method is used to get the current WebSocket handlers.
     * @return The map of WebSocket sessions to WebHandler instances.
    */
    public static Map<Session, WebHandler> getHandlerMap() {
        return HANDLER_MAP;
    }

    /**
     * Handles the opening of a WebSocket connection.
     * This method is called when a new WebSocket connection is established.
     * @param session The WebSocket session that was opened.
    */
    @OnOpen
    public void onOpen(Session session) {
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
            hub.addClient(handler);
            System.out.println(handler.getUsername() + " connected on WebSocket: " + session.getId());

            // Broadcast the "hello" message to all connected clients.
            hub.broadcast(ChatMessage.hello(msg.getUser(), null));
            return;
        }

        // If the handler is authenticated, process the message.
        // If the message is a command, handle it accordingly.
        if (handler != null && handler.isAuthenticated()) {
            if (CommandHelper.command(msg, hub, handler)) {
                return;
            }
            if ("text".equals(msg.getType())) {
                ChatMessage stamped = ChatMessage.of(handler.getUsername(), msg.getBody());
                hub.broadcast(stamped);
            } else {
                hub.broadcast(msg);
            }
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
            hub.broadcast(ChatMessage.bye(handler.getUsername()));
            hub.releaseName(handler.getUsername());
            hub.removeClient(handler);
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
