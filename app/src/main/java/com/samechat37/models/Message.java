package com.samechat37.models;

public class Message {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;
    private boolean seen;

    public Message() {
        // Required for Firebase
    }

    public Message(String messageId, String senderId, String receiverId, String text, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
        this.seen = false;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }
}
