package com.vn.ecm.dto;

import com.vn.ecm.entity.DriveItemType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ShareLinkItemDto (
    UUID id,
    String name,
    DriveItemType type,
    String owner,
    LocalDateTime sharedDate,
    String permissionType,
    boolean active
) {}
