package com.vn.ecm.view.ecm;

import com.vn.ecm.view.component.filepreview.*;
import io.jmix.core.FileRef;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.DialogWindow;
import io.jmix.flowui.view.View;
import org.springframework.stereotype.Service;

@Service
public class FilePreviewUntil {
    private final Notifications notifications;
    private final DialogWindows dialogWindows;

    public FilePreviewUntil(Notifications notifications, DialogWindows dialogWindows) {
        this.notifications = notifications;
        this.dialogWindows = dialogWindows;
    }

    public void previewFile(FileRef fileRef, String fileName, View<?> parentView) {
        if (fileRef == null) {
            notifications.create("Không tìm thấy file để xem trước")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        String ext = extractExtension(fileName);
        String extension = ext.toLowerCase();
        switch (extension) {
            case "pdf" -> previewPdfFile(fileRef , parentView);
            case "txt" -> previewTextFile(fileRef , parentView);
            case "doc" , "docx" -> previewDocxFile(fileRef , parentView);
            case "jpg", "jpeg", "png", "webp", "svg", "gif" -> previewImageFile(fileRef , parentView);
            case "mp4", "mov", "webm" -> preViewVideoFile(fileRef , parentView);
            case "html", "htm", "java", "js", "css", "md", "xml", "sql" ->
                    preViewHtmlFile(fileRef ,  parentView);
            case "xlsx", "xls" -> previewExcelFile(fileRef , parentView);
            case "zip" -> previewZipFile(fileRef, parentView);
            default -> notifications.create("Loại file chưa hỗ trợ: " + extension)
                    .withType(Notifications.Type.WARNING)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();
        }
    }

    private void previewPdfFile(FileRef fileRelf, View<?> parentView) {
        DialogWindow<PdfPreview> window = dialogWindows.view(parentView, PdfPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewTextFile(FileRef fileRelf , View<?> parentView) {
        DialogWindow<TextPreview> window = dialogWindows.view(parentView, TextPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewImageFile(FileRef fileRelf , View<?> parentView) {
        DialogWindow<ImagePreview> window = dialogWindows.view(parentView, ImagePreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void preViewVideoFile(FileRef fileRelf, View<?> parentView) {
        DialogWindow<VideoPreview> window = dialogWindows.view(parentView, VideoPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void preViewHtmlFile(FileRef fileRelf,  View<?> parentView) {
        DialogWindow<CodePreview> window = dialogWindows.view(parentView, CodePreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }
    private void previewExcelFile(FileRef fileRelf , View<?> parentView) {
        DialogWindow<ExcelPreview> window = dialogWindows.view(parentView, ExcelPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }
    private void previewZipFile(FileRef fileRef, View<?> parentView) {
        DialogWindow<ZipPreview> window =
                dialogWindows.view(null, ZipPreview.class).build();

        window.getView().setInputFile(fileRef);
        window.setResizable(true);
        window.open();
    }


    private void previewDocxFile(FileRef fileRef, View<?> parentView) {
        DialogWindow<DocxPreview> window =
                dialogWindows.view(parentView, DocxPreview.class)
                        .build();

        window.getView().setInputFile(fileRef);
        window.setResizable(true);
        window.open();
    }


    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        return (i > 0 ? fileName.substring(i + 1) : "");
    }
}
