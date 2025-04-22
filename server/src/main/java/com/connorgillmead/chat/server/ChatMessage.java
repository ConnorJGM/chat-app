package com.connorgillmead.chat.server;

import com.google.gson.Gson;

/**
 *
 */
public final class ChatMessage {
    private static final Gson GSON = new Gson();

    private String type;
    private String user;
    private String body;
    private long   time;

    public String getType() {
        return type;
    }

    public String getUser() {
        return user;
    }

    public String getBody() {
        return body;
    }

    public long getTime() {
        return time;
    }

    /**
     * Creates a new ChatMessage object with the specified user and body.
     * The time is set to the current time in milliseconds since the epoch.
     *
     * @param user The user who sent the message.
     * @param body The body of the message.
     * @return A new ChatMessage object with the specified user and body.
     */
    public static ChatMessage of(String user, String body) {
        ChatMessage m = new ChatMessage();
        m.user = user;
        m.body = body;
        m.time = System.currentTimeMillis();
        return m;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ChatMessage fromJson(String j) {
        return GSON.fromJson(j, ChatMessage.class);
    }
}
