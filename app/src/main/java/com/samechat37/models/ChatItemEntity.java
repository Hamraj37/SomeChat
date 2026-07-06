package com.samechat37.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_items")
public class ChatItemEntity {
    @PrimaryKey
    @NonNull
    private String uid;
    private String name;
    private String lastMessage;
    private String time;
    private String photoUrl;
    private boolean online;
    private long timestamp;
    private int unreadCount;
    private String lastMessageType;

    public ChatItemEntity(@NonNull String uid, String name, String lastMessage, String time, String photoUrl, boolean online, long timestamp, int unreadCount, String lastMessageType) {
        this.uid = uid;
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
        this.photoUrl = photoUrl;
        this.online = online;
        this.timestamp = timestamp;
        this.unreadCount = unreadCount;
        this.lastMessageType = lastMessageType;
    }

    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public String getLastMessageType() { return lastMessageType; }
    public void setLastMessageType(String lastMessageType) { this.lastMessageType = lastMessageType; }
}
