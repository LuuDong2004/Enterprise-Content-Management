package com.vn.ecm.entity;

import io.jmix.core.metamodel.datatype.EnumClass;

public enum DriveItemType implements EnumClass<String> {
    // ===== FOLDER =====
    FOLDER("folder", "Folder", "folder"),

    // ===== DOCUMENTS =====
    DOCUMENT("document", "Google Docs", "doc"),
    SPREADSHEET("spreadsheet", "Google Sheets", "xls"),
    PRESENTATION("presentation", "Google Slides", "ppt"),
    PDF("pdf", "PDF", "pdf"),

    // ===== TEXT / CODE =====
    TEXT("text", "Text file", "txt"),
    CODE("code", "Code", "code"),

    // ===== MEDIA =====
    IMAGE("image", "Image", "img"),
    VIDEO("video", "Video", "video"),
    AUDIO("audio", "Audio", "audio"),

    // ===== ARCHIVE =====
    ZIP("zip", "Compressed", "zip"),

    // ===== OTHER =====
    UNKNOWN("unknown", "File", "file");

    private final String code;       // gửi cho FE
    private final String label;      // hiển thị
    private final String iconKey;    // FE map icon

    DriveItemType(String code, String label, String iconKey) {
        this.code = code;
        this.label = label;
        this.iconKey = iconKey;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getIconKey() {
        return iconKey;
    }

    @Override
    public String getId() {
        return "";
    }

}
