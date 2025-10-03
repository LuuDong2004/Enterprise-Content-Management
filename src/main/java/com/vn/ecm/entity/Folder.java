package com.vn.ecm.entity;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@JmixEntity(name = "FOLDER")
public class Folder {
    @JmixId
    private UUID id;
    private String name;
    private Folder parent;
    @OneToMany(mappedBy = "parent")
    private List<Folder> children;
    @OneToMany(mappedBy = "folder")
    private List<File> file;
    private LocalDateTime createdDate;
}
