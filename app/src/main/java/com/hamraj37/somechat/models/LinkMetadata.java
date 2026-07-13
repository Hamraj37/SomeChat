package com.hamraj37.somechat.models;

public class LinkMetadata {
    private String url;
    private String title;
    private String description;
    private String imageUrl;

    public LinkMetadata(String url, String title, String description, String imageUrl) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
}
