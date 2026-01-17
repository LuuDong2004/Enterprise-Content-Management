package com.vn.ecm.dto;

import java.util.UUID;

public class ShareRequestDto {
    private UUID folderId;
    private UUID fileId;
    private String recipientEmail;
    private String permissionType; // "READ", "MODIFY", "FULL"

    // Getters and Setters
    public UUID getFolderId() { return folderId; }
    public void setFolderId(UUID folderId) { this.folderId = folderId; }
    public UUID getFileId() { return fileId; }
    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
}
