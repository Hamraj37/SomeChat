package com.samechat37.ui.transform;

public class ChatItem {
    private final String name;
    private final String lastMessage;
    private final String time;
    private final int avatarResId;
    private final String uid;
    private final String photoUrl;
    private final boolean online;
    private final long timestamp;

    public ChatItem(String name, String lastMessage, String time, int avatarResId, String uid, String photoUrl, boolean online, long timestamp) {
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
        this.avatarResId = avatarResId;
        this.uid = uid;
        this.photoUrl = photoUrl;
        this.online = online;
        this.timestamp = timestamp;
    }

    public String getName() { return name; }
    public String getLastMessage() { return lastMessage; }
    public String getTime() { return time; }
    public int getAvatarResId() { return avatarResId; }
    public String getUid() { return uid; }
    public String getPhotoUrl() { return photoUrl; }
    public boolean isOnline() { return online; }
    public long getTimestamp() { return timestamp; }
}