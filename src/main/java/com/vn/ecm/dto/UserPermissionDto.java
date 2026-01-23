package com.vn.ecm.dto;

import java.util.UUID;

public class UserPermissionDto {
    private UUID userId;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl; // Optional
    private String permissionType; // READ, MODIFY, FULL
    private Boolean isOwner;

    public UserPermissionDto() {
    }

    public UserPermissionDto(UUID userId, String username, String email, String fullName, String permissionType, Boolean isOwner) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.permissionType = permissionType;
        this.isOwner = isOwner;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(String permissionType) {
        this.permissionType = permissionType;
    }

    public Boolean getIsOwner() {
        return isOwner;
    }

    public void setIsOwner(Boolean isOwner) {
        this.isOwner = isOwner;
    }
}
