package com.samechat37.models;

public class Message {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private long timestamp;
    private boolean seen;
    private String type; // "text" or "voice"
    private String mediaUrl;
    private int duration;
    private boolean forwarded;
    
    // Reply fields
    private String replyToId;
    private String replyToName;
    private String replyToText;

    public Message() {
        // Required for Firebase
    }

    public Message(String messageId, String senderId, String receiverId, String text, long timestamp) {
        this(messageId, senderId, receiverId, text, timestamp, "text", null, 0);
    }

    public Message(String messageId, String senderId, String receiverId, String text, long timestamp, String type, String mediaUrl, int duration) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
        this.seen = false;
        this.type = type;
        this.mediaUrl = mediaUrl;
        this.duration = duration;
    }

    // Getters and setters for reply fields
    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId; }

    public String getReplyToName() { return replyToName; }
    public void setReplyToName(String replyToName) { this.replyToName = replyToName; }

    public String getReplyToText() { return replyToText; }
    public void setReplyToText(String replyToText) { this.replyToText = replyToText; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public boolean isForwarded() { return forwarded; }
    public void setForwarded(boolean forwarded) { this.forwarded = forwarded; }
}
