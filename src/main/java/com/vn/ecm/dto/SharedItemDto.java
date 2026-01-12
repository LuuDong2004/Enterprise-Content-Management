package com.vn.ecm.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class SharedItemDto {
    private UUID id;
    private String name;
    private String type; // "folder" or "file"
    private String owner;
    private LocalDateTime sharedDate;
    private Integer permissionMask;
    private String permissionType; // "Viewer", "Editor", "Owner"

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public LocalDateTime getSharedDate() { return sharedDate; }
    public void setSharedDate(LocalDateTime sharedDate) { this.sharedDate = sharedDate; }
    public Integer getPermissionMask() { return permissionMask; }
    public void setPermissionMask(Integer permissionMask) { this.permissionMask = permissionMask; }
    public String getPermissionType() { return permissionType; }
    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }
}
