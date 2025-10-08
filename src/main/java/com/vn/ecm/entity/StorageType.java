package com.vn.ecm.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;


public enum StorageType implements EnumClass<String> {

    ;

    private final String id;

    StorageType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static StorageType fromId(String id) {
        for (StorageType at : StorageType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}