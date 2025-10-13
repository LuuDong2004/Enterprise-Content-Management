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

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_ID")
    private Folder parent;

    @OneToMany(mappedBy = "parent")
    @OrderBy("name ASC")
    private List<Folder> children;

    @OneToMany(mappedBy = "folder")
    private List<FileDescriptor> file;

    @Column(name = "createdDate")
    private LocalDateTime createdDate;

    public Folder() {
    }

    public Folder(UUID id, String name, Folder parent, List<Folder> children, List<FileDescriptor> file, LocalDateTime createdDate) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.children = children;
        this.file = file;
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

    public List<Folder> getChildren() {
        return children;
    }

    public void setChildren(List<Folder> children) {
        this.children = children;
    }

    public List<FileDescriptor> getFile() {
        return file;
    }

    public void setFile(List<FileDescriptor> file) {
        this.file = file;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

}
