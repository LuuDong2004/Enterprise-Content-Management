package com.vn.ecm.component;

import com.vn.ecm.entity.DriveItemType;
import com.vn.ecm.entity.FileDescriptor;
import org.springframework.stereotype.Component;

@Component
public class DriveItemTypeResolver {

    public DriveItemType resolve(FileDescriptor file) {
        if (file == null || file.getName() == null) {
            return DriveItemType.UNKNOWN;
        }

        String name = file.getName().toLowerCase();

        if (name.endsWith(".doc") || name.endsWith(".docx")) {
            return DriveItemType.DOCUMENT;
        }
        if (name.endsWith(".xls") || name.endsWith(".xlsx")) {
            return DriveItemType.SPREADSHEET;
        }
        if (name.endsWith(".ppt") || name.endsWith(".pptx")) {
            return DriveItemType.PRESENTATION;
        }
        if (name.endsWith(".pdf")) {
            return DriveItemType.PDF;
        }
        if (name.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) {
            return DriveItemType.IMAGE;
        }
        if (name.matches(".*\\.(mp4|mkv|avi)$")) {
            return DriveItemType.VIDEO;
        }
        if (name.matches(".*\\.(mp3|wav|ogg)$")) {
            return DriveItemType.AUDIO;
        }
        if (name.matches(".*\\.(zip|rar|7z)$")) {
            return DriveItemType.ZIP;
        }
        if (name.matches(".*\\.(java|js|ts|py|sql|xml|json|yml)$")) {
            return DriveItemType.CODE;
        }
        if (name.endsWith(".txt")) {
            return DriveItemType.TEXT;
        }

        return DriveItemType.UNKNOWN;
    }
}
