package com.hamraj37.somechat.models;

import java.io.Serializable;
import java.util.List;

public class Status implements Serializable {
    private String statusId;
    private String userId;
    private String userName;
    private String profilePic;
    private long lastUpdated;
    private List<StatusItem> items;

    public Status() {}

    public Status(String statusId, String userId, String userName, String profilePic, long lastUpdated, List<StatusItem> items) {
        this.statusId = statusId;
        this.userId = userId;
        this.userName = userName;
        this.profilePic = profilePic;
        this.lastUpdated = lastUpdated;
        this.items = items;
    }

    public String getStatusId() { return statusId; }
    public void setStatusId(String statusId) { this.statusId = statusId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getProfilePic() { return profilePic; }
    public void setProfilePic(String profilePic) { this.profilePic = profilePic; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<StatusItem> getItems() { return items; }
    public void setItems(List<StatusItem> items) { this.items = items; }

    public static class StatusItem implements Serializable {
        private String id;
        private String mediaUrl;
        private String type; // "image" or "video"
        private long timestamp;
        private String caption;
        private java.util.Map<String, Long> views; // userId -> timestamp

        public StatusItem() {}

        public StatusItem(String id, String mediaUrl, String type, long timestamp, String caption) {
            this.id = id;
            this.mediaUrl = mediaUrl;
            this.type = type;
            this.timestamp = timestamp;
            this.caption = caption;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }

        public java.util.Map<String, Long> getViews() { return views; }
        public void setViews(java.util.Map<String, Long> views) { this.views = views; }
    }
}
