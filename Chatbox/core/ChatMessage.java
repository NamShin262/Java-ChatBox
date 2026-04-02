package Chatbox.core;

public class ChatMessage {
    private final String from;
    private final String content;

    public ChatMessage(String from, String content) {
        this.from = from;
        this.content = content;
    }

    public String getFrom() {
        return from;
    }

    public String getContent() {
        return content;
    }
}