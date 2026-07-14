package com.hamraj37.somechat.models;

public class CallLog {
    private String callId;
    private String callerId;
    private String receiverId;
    private String otherUserId;
    private String otherUserName;
    private String otherUserAvatar;
    private boolean isVideo;
    private boolean isIncoming;
    private String status; // calling, ringing, accepted, ended, missed, rejected
    private long timestamp;
    private long duration;

    public CallLog() {
    }

    public CallLog(String callId, String callerId, String receiverId, String otherUserId, String otherUserName, String otherUserAvatar, boolean isVideo, boolean isIncoming, String status, long timestamp, long duration) {
        this.callId = callId;
        this.callerId = callerId;
        this.receiverId = receiverId;
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.otherUserAvatar = otherUserAvatar;
        this.isVideo = isVideo;
        this.isIncoming = isIncoming;
        this.status = status;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getOtherUserId() { return otherUserId; }
    public void setOtherUserId(String otherUserId) { this.otherUserId = otherUserId; }

    public String getOtherUserName() { return otherUserName; }
    public void setOtherUserName(String otherUserName) { this.otherUserName = otherUserName; }

    public String getOtherUserAvatar() { return otherUserAvatar; }
    public void setOtherUserAvatar(String otherUserAvatar) { this.otherUserAvatar = otherUserAvatar; }

    public boolean isVideo() { return isVideo; }
    public void setVideo(boolean video) { isVideo = video; }

    public boolean isIncoming() { return isIncoming; }
    public void setIncoming(boolean incoming) { isIncoming = incoming; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}
