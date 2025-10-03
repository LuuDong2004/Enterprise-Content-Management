package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity(name = "FILE")
public class File {
    @JmixId
    private UUID id;

    private String name;

    private String extension;

    private Long size;

    private LocalDateTime lastModified;

    @ManyToOne
    private Folder folder;

}
