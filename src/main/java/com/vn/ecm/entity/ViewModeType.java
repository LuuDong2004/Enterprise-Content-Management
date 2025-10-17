package com.vn.ecm.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

import org.springframework.lang.Nullable;

public enum ViewModeType implements EnumClass<String> {

    DEFAULT("DEFAULT"),
    LIST("LIST"),
    MEDIUM_ICONS("MEDIUM_ICONS");

    private final String id;

    ViewModeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static ViewModeType fromId(String id) {
        for (ViewModeType at : ViewModeType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}