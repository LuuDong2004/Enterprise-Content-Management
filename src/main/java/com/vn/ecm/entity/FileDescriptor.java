package com.vn.ecm.entity;
import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "FILE_Descriptor")
@Entity
public class FileDescriptor {
    @JmixGeneratedValue
    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Column(name = "NAME", columnDefinition = "NVARCHAR(255)", nullable = false)
    private String name;

    @Column(name = "STORAGE")
    private String storage;

    @Column(name = "EXTENSION")
    private String extension;

    @Column(name = "SIZE")
    private Long size;

    @Column(name = "LASTMODIFIED")
    private LocalDateTime lastModified;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOLDER_ID")
    private Folder folder;

    @Column(name = "FILE_REF")
    private FileRef fileRef;

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public FileDescriptor() {
    }

    public FileDescriptor(UUID id, String name, String extension, Long size, LocalDateTime lastModified, Folder folder, FileRef fileRef) {
        this.id = id;
        this.name = name;
        this.extension = extension;
        this.size = size;
        this.lastModified = lastModified;
        this.folder = folder;
        this.fileRef = fileRef;
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

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
    }
    public FileRef getFileRef() {
        return fileRef;
    }
    public void setFileRef(FileRef fileRef) {
        this.fileRef = fileRef;
    }
}
