package com.hamraj37.somechat.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Highlight implements Serializable {
    private String highlightId;
    private String userId;
    private String title;
    private String coverUrl;
    private List<Status.StatusItem> items;

    public Highlight() {
        this.items = new ArrayList<>();
    }

    public Highlight(String highlightId, String userId, String title, String coverUrl) {
        this.highlightId = highlightId;
        this.userId = userId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.items = new ArrayList<>();
    }

    public String getHighlightId() { return highlightId; }
    public void setHighlightId(String highlightId) { this.highlightId = highlightId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public List<Status.StatusItem> getItems() { return items; }
    public void setItems(List<Status.StatusItem> items) { this.items = items; }
}
