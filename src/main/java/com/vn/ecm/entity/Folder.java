package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "FOLDER")
@Entity
public class Folder {
    @JmixGeneratedValue
    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Column(name = "FULL_PATH")
    private String fullPath;

    @Column(name = "STORAGE")
    private String storage;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_ID")
    private Folder parent;

    @OneToMany(mappedBy = "folder")
    private List<FileDescriptor> fileDescriptor;

    @Column(name = "createdDate")
    private LocalDateTime createdDate;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }


    public Folder() {
    }

    public Folder(UUID id, String name, Folder parent, List<Folder> children, List<FileDescriptor> fileDescriptor, LocalDateTime createdDate) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.fileDescriptor = fileDescriptor;
        this.createdDate = createdDate;
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


    public List<FileDescriptor> getFile() {
        return fileDescriptor;
    }

    public void setFile(List<FileDescriptor> fileDescriptor) {
        this.fileDescriptor = fileDescriptor;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
}
