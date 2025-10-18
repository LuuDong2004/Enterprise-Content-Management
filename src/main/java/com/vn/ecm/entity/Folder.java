package com.vn.ecm.entity;
//v1
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;
@JmixEntity
@Table(name = "FOLDER")
@Entity
public class Folder {
    @JmixGeneratedValue
    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_ID")
    private Folder parent;

    @Column(name = "FULL_PATH")
    private String fullPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SOURCE_STORAGE_ID")   // <-- dùng đúng 1 cột FK
    private SourceStorage sourceStorage;

    @Column(name = "createdDate")
    private LocalDateTime createdDate;

    @Column(name = "IN_TRASH")
    private Boolean inTrash;

    @Column(name = "DELETE_DATE")
    private LocalDateTime deleteDate;

    @Column(name = "DELETED_BY")
    private String deletedBy;

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDateTime getDeleteDate() {
        return deleteDate;
    }

    public void setDeleteDate(LocalDateTime deleteDate) {
        this.deleteDate = deleteDate;
    }

    public Boolean getInTrash() {
        return inTrash;
    }

    public void setInTrash(Boolean inTrash) {
        this.inTrash = inTrash;
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage;
    }

    public void setSourceStorage(SourceStorage sourceStorage) {
        this.sourceStorage = sourceStorage;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Folder getParent() {
        return parent;
    }
    public void setParent(Folder parent) {
        this.parent = parent;
    }
    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
}
