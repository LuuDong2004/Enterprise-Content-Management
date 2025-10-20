package com.vn.ecm.ecmmodule.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum AppliesTo implements EnumClass<String> {

    THIS_FOLDER_ONLY("Chỉ thư mục"),
    THIS_FOLDER_SUBFOLDERS_FILES("Thư mục, các thư mục con, tệp"),
    THIS_FOLDER_SUBFOLDERS("Thư mục, các thư mục con"),
    THIS_FOLDER_FILES("Thư mục và tệp"),
    SUBFOLDERS_FILES_ONLY("Chỉ các thư mục con và tệp");

    private final String id;

    AppliesTo(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return "";
    }

    public static AppliesTo fromId(String id) {
        for (AppliesTo a : AppliesTo.values()) {
            if (a.getId().equals(id)) {
                return a;
            }
        }
        return null;
    }

}