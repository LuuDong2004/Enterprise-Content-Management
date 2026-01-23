package com.vn.ecm.service.ecm.folderandfile;

import com.vn.ecm.entity.FileType;
import org.springframework.stereotype.Service;

@Service
public final class FileTypeResolver {

    private FileTypeResolver() {}

    public static FileType resolve(String extension) {
        if (extension == null) return FileType.OTHER;

        switch (extension.toLowerCase()) {

            // ===== DOCUMENT =====
            case "pdf":
                return FileType.PDF;

            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
                return FileType.OFFICE;

            // ===== IMAGE =====
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "bmp":
            case "svg":
                return FileType.IMAGE;

            // ===== VIDEO =====
            case "mp4":
            case "webm":
            case "avi":
            case "mkv":
            case "mov":
                return FileType.VIDEO;

            // ===== AUDIO =====
            case "mp3":
            case "wav":
            case "aac":
            case "ogg":
                return FileType.AUDIO;

            // ===== TEXT =====
            case "txt":
            case "md":
            case "csv":
            case "log":
                return FileType.TEXT;

            // ===== HTML =====
            case "html":
            case "htm":
                return FileType.HTML;

            // ===== ARCHIVE =====
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
                return FileType.ARCHIVE;

            // ===== SOURCE CODE =====
            case "java":
            case "js":
            case "ts":
            case "jsx":
            case "tsx":
            case "py":
            case "go":
            case "c":
            case "cpp":
            case "cs":
            case "php":
            case "sql":
            case "xml":
            case "json":
            case "yml":
            case "yaml":
                return FileType.CODE;

            default:
                return FileType.OTHER;
        }
    }
}
