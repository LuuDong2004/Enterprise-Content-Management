package com.vn.ecm.view.component.filepreview;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.codeeditor.CodeEditor;
import io.jmix.flowui.kit.component.codeeditor.CodeEditorMode;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@ViewController(id = "HtmlPreview")
@ViewDescriptor(path = "code-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class CodePreview extends StandardView {

    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    private FileRef inputFile;
    @Autowired
    private Notifications notifications;
    @ViewComponent
    private CodeEditor codePreview;

    public void setInputFile(FileRef inputFile) {
        this.inputFile = inputFile;
    }

    private String content;
    private String fileName;

    public void setContent(String content) {
        this.content = content;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        if (content != null) {
            if (fileName != null) {
                codePreview.setLabel(fileName);
            }
            codePreview.setValue(content);
            return;
        }
        if (inputFile == null) {
            return;
        }
        String fileName = inputFile.getFileName();
        try {
            String storageName = inputFile.getStorageName();
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);

            try (InputStream is = storage.openStream(inputFile)) {
                byte[] bytes = is.readAllBytes();
                String text = decodeText(bytes);
                codePreview.setValue(text);
                codePreview.setReadOnly(true);
                codePreview.setMode(detectMode(fileName));
            }
        } catch (Exception e) {
            notifications.create("Không đọc được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
        }
    }
    private String decodeText(byte[] b) {
        if (b.length >= 3 && b[0] == (byte)0xEF && b[1] == (byte)0xBB && b[2] == (byte)0xBF)
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);        // UTF-8 BOM

        if (b.length >= 2) {
            if (b[0] == (byte)0xFF && b[1] == (byte)0xFE)
                return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE); // UTF-16 LE
            if (b[0] == (byte)0xFE && b[1] == (byte)0xFF)
                return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE); // UTF-16 BE
        }

        return new String(b, StandardCharsets.UTF_8); // Default UTF-8
    }
    public CodeEditorMode detectMode(String fileName) {
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "html", "htm" -> CodeEditorMode.HTML;
            case "css" -> CodeEditorMode.CSS ;
            case "js" -> CodeEditorMode.JAVASCRIPT;
            case "json" -> CodeEditorMode.JSON;
            case "xml" -> CodeEditorMode.XML;
            case "java" -> CodeEditorMode.JAVA;
            case "sql" -> CodeEditorMode.SQLSERVER;
            case "md" -> CodeEditorMode.MARKDOWN;
            default -> CodeEditorMode.PLAIN_TEXT;
        };
    }
}