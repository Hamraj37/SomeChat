package com.hamraj37.somechat.ui.transform;

public class ChatItem {
    private final String name;
    private final String lastMessage;
    private final String time;
    private final int avatarResId;
    private final String uid;
    private final String photoUrl;
    private final boolean online;
    private final long timestamp;
    private final int unreadCount;
    private final String lastMessageType;

    public ChatItem(String name, String lastMessage, String time, int avatarResId, String uid, String photoUrl, boolean online, long timestamp, int unreadCount, String lastMessageType) {
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
        this.avatarResId = avatarResId;
        this.uid = uid;
        this.photoUrl = photoUrl;
        this.online = online;
        this.timestamp = timestamp;
        this.unreadCount = unreadCount;
        this.lastMessageType = lastMessageType;
    }

    public String getName() { return name; }
    public String getLastMessage() { return lastMessage; }
    public String getTime() { return time; }
    public int getAvatarResId() { return avatarResId; }
    public String getUid() { return uid; }
    public String getPhotoUrl() { return photoUrl; }
    public boolean isOnline() { return online; }
    public long getTimestamp() { return timestamp; }
    public int getUnreadCount() { return unreadCount; }
    public String getLastMessageType() { return lastMessageType; }
}