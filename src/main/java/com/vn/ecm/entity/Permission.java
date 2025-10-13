package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Table(name = "PERMISSION", indexes = {
        @Index(name = "IDX_PERMISSION_USER", columnList = "USER_ID"),
        @Index(name = "IDX_PERMISSION_FOLDER", columnList = "FOLDER_ID"),
        @Index(name = "IDX_PERMISSION_FILE", columnList = "FILE_ID")
})
@Entity
public class Permission {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "INHERITED_FROM")
    private String inheritedFrom;

    @Column(name = "APPLIES_TO")
    private String appliesTo;

    @JoinColumn(name = "FOLDER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Folder folder;

    @JoinColumn(name = "FILE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private FileDescriptor file;

    @JoinColumn(name = "USER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @Column(name = "INHERITED")
    private Boolean inherited;

    @Column(name = "INHERIT_ENABLED")
    private Boolean inheritEnabled;

    @Column(name = "PERMISSION_TYPE")
    private Integer permissionType;

    @Column(name = "PERMISSION_MASK")
    private Integer permissionMask;

    @Column(name = "ROLE_CODE")
    private String roleCode;

    @Transient
    private Boolean allow;

    public FileDescriptor getFile() {
        return file;
    }

    public void setFile(FileDescriptor file) {
        this.file = file;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
    }

    public AppliesTo getAppliesTo() {
        return AppliesTo.fromId(appliesTo);
    }

    public void setAppliesTo(AppliesTo appliesTo) {
        this.appliesTo = appliesTo == null ? null : appliesTo.getId();
    }

    public String getInheritedFrom() {
        return inheritedFrom;
    }

    public void setInheritedFrom(String inheritedFrom) {
        this.inheritedFrom = inheritedFrom;
    }

    public Boolean getAllow() {
        return allow;
    }

    public void setAllow(Boolean allow) {
        this.allow = allow;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Integer getPermissionMask() {
        return permissionMask;
    }

    public void setPermissionMask(Integer permissionMask) {
        this.permissionMask = permissionMask;
    }

    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType == null ? null : permissionType.getId();
    }

    public PermissionType getPermissionType() {
        return permissionType == null ? null : PermissionType.fromId(permissionType);
    }

    public Boolean getInheritEnabled() {
        return inheritEnabled;
    }

    public void setInheritEnabled(Boolean inheritEnabled) {
        this.inheritEnabled = inheritEnabled;
    }

    public Boolean getInherited() {
        return inherited;
    }

    public void setInherited(Boolean inherited) {
        this.inherited = inherited;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}