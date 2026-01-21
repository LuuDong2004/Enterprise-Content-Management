package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "SHARE_PERMISSION_REQUEST", indexes = {
        @Index(name = "IDX_SHARE_PERM_REQ_SHARE_LINK", columnList = "SHARE_LINK_ID"),
        @Index(name = "IDX_SHARE_PERM_REQ_REQUESTER", columnList = "REQUESTER_EMAIL")
})

@Entity
public class SharePermissionRequest {
    @JmixGeneratedValue
    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SHARE_LINK_ID", nullable = false)
    private ShareLink shareLink;

    @Column(name = "REQUESTER_EMAIL", nullable = false)
    private String requesterEmail;

    @Column(name = "REQUESTED_PERMISSION", nullable = false)
    private String requestedPermission; // MODIFY, FULL

    @Column(name = "MESSAGE")
    @Lob
    private String message;

    @Column(name = "STATUS", nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "CREATED_DATE", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "RESPONDED_DATE")
    private LocalDateTime respondedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESPONDED_BY_ID")
    private User respondedBy;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public ShareLink getShareLink() {
        return shareLink;
    }

    public void setShareLink(ShareLink shareLink) {
        this.shareLink = shareLink;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getRequestedPermission() {
        return requestedPermission;
    }

    public void setRequestedPermission(String requestedPermission) {
        this.requestedPermission = requestedPermission;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getRespondedDate() {
        return respondedDate;
    }

    public void setRespondedDate(LocalDateTime respondedDate) {
        this.respondedDate = respondedDate;
    }

    public User getRespondedBy() {
        return respondedBy;
    }

    public void setRespondedBy(User respondedBy) {
        this.respondedBy = respondedBy;
    }
}
