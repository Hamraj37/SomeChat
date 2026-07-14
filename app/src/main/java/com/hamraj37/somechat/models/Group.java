package com.hamraj37.somechat.models;

import java.io.Serializable;
import java.util.Map;

public class Group implements Serializable {
    private String groupId;
    private String groupName;
    private String groupAvatar;
    private String createdBy;
    private long createdAt;
    private Map<String, Boolean> members; // userId -> true
    private Map<String, Boolean> admins; // userId -> true

    public Group() {
    }

    public Group(String groupId, String groupName, String groupAvatar, String createdBy, long createdAt, Map<String, Boolean> members) {
        this(groupId, groupName, groupAvatar, createdBy, createdAt, members, null);
    }

    public Group(String groupId, String groupName, String groupAvatar, String createdBy, long createdAt, Map<String, Boolean> members, Map<String, Boolean> admins) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.groupAvatar = groupAvatar;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.members = members;
        this.admins = admins;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupAvatar() {
        return groupAvatar;
    }

    public void setGroupAvatar(String groupAvatar) {
        this.groupAvatar = groupAvatar;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Boolean> getMembers() {
        return members;
    }

    public void setMembers(Map<String, Boolean> members) {
        this.members = members;
    }

    public Map<String, Boolean> getAdmins() {
        return admins;
    }

    public void setAdmins(Map<String, Boolean> admins) {
        this.admins = admins;
    }
}
