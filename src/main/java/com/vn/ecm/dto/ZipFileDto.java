package com.vn.ecm.dto;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.JmixEntity;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JmixEntity
public class ZipFileDto {

    @JmixId
    @JmixGeneratedValue
    private UUID id;


    private String name;

    private Long size;


    private String key;


    private ZipFileDto parent;


    private Boolean folder;


    private List<ZipFileDto> children = new ArrayList<>();


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

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public ZipFileDto getParent() {
        return parent;
    }

    public void setParent(ZipFileDto parent) {
        this.parent = parent;
    }

    public Boolean getFolder() {
        return folder;
    }

    public void setFolder(Boolean folder) {
        this.folder = folder;
    }

    public List<ZipFileDto> getChildren() {
        return children;
    }

    public void setChildren(List<ZipFileDto> children) {
        this.children = children;
    }
}
