package com.vn.ecm.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum FileType implements EnumClass<String> {

    PDF("PDF"),
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    AUDIO("AUDIO"),
    TEXT("TEXT"),
    HTML("HTML"),
    OFFICE("OFFICE"),
    ARCHIVE("ARCHIVE"),
    CODE("CODE"),
    OTHER("OTHER");


    private final String id;

    FileType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static FileType fromId(String id) {
        for (FileType at : FileType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}