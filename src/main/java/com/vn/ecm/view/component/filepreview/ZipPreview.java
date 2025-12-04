package com.vn.ecm.view.component.filepreview;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vn.ecm.dto.ZipFileDto;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.service.ecm.zipfile.ZipPreviewService;
import io.jmix.core.FileRef;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import net.lingala.zip4j.exception.ZipException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@ViewController("zipPreview")
@ViewDescriptor("zip-preview.xml")
@DialogMode(width = "80%", height = "100%")
public class ZipPreview extends StandardView {

    private FileRef inputFile;

    @Autowired
    private ZipPreviewService zipPreviewService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private Downloader downloader;

    @Autowired
    private UiComponents uiComponents;

    @ViewComponent
    private TreeDataGrid<ZipFileDto> zipTreeGrid;
    @ViewComponent
    private CollectionContainer<ZipFileDto> zipTreeDc;

    private String currentPassword;
    @Autowired
    private DialogWindows dialogWindows;

    public void setInputFile(FileRef inputFile) {

        this.inputFile = inputFile;
    }

    @Subscribe
    public void onInit(final InitEvent event) {
        initFolderGridColumn();
    }


    @Subscribe
    public void onReady(ReadyEvent event) {
        if (inputFile == null) {
            notifications.create("Không có file nén để xem trước")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        try {
            buildTreeAndFill(null);

        } catch (ZipException ze) {
            openPasswordDialog();

        } catch (Exception e) {
            e.printStackTrace();
            notifications.create("Không kiểm tra được file nén: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void buildTreeAndFill(String password) throws Exception {
        List<ZipFileDto> roots = zipPreviewService.buildZipTree(inputFile, password);
        this.currentPassword = password;

        List<ZipFileDto> allNodes = new ArrayList<>();
        for (ZipFileDto root : roots) {
            collectNodes(root, null, allNodes);
        }

        zipTreeDc.setItems(allNodes);

        if (!roots.isEmpty()) {
            zipTreeGrid.expand(roots.get(0));
        }
    }

    private void collectNodes(ZipFileDto fileZipDto, ZipFileDto parent, List<ZipFileDto> list) {
        fileZipDto.setParent(parent);
        list.add(fileZipDto);
        if (fileZipDto.getChildren() != null) {
            for (ZipFileDto child : fileZipDto.getChildren()) {
                collectNodes(child, fileZipDto, list);
            }
        }
    }

    private void openPasswordDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Tệp nén có mật khẩu");

        PasswordField passwordField = new PasswordField("Mật khẩu");
        passwordField.setWidthFull();

        Button unZipBtn = new Button("Giải nén", e -> {
            dialog.close();
            try {
                buildTreeAndFill(passwordField.getValue());
            } catch (ZipException ze) {
                notifications.create("Mật khẩu không đúng, vui lòng nhập lại.")
                        .withType(Notifications.Type.ERROR)
                        .show();
                openPasswordDialog();
            } catch (Exception ex) {
                notifications.create("Lỗi giải nén: " + ex.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        });

        VerticalLayout layout = new VerticalLayout(passwordField, unZipBtn);
        layout.setPadding(false);
        layout.setSpacing(true);
        dialog.add(layout);
        dialog.open();
    }

    @Subscribe("zipTreeGrid.downloadAction")
    public void onDownload(ActionPerformedEvent event) {
        ZipFileDto selected = zipTreeGrid.getSingleSelectedItem();
        if (selected == null || Boolean.TRUE.equals(selected.getFolder())) {
            notifications.create("Vui lòng chọn file (không phải thư mục) để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            byte[] bytes = zipPreviewService.loadEntryBytes(
                    inputFile,
                    selected.getKey(),
                    currentPassword
            );

            String fileName = selected.getName() != null ? selected.getName() : "file";
            downloader.download(bytes, fileName, DownloadFormat.OCTET_STREAM);

        } catch (ZipException ze) {
            notifications.create("Không tải được file (có thể mật khẩu sai hoặc thiếu).")
                    .withType(Notifications.Type.ERROR)
                    .show();
        } catch (Exception e) {
            notifications.create("Không tải được file: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    private void initFolderGridColumn() {
        if (zipTreeGrid.getColumnByKey("name") != null) {
            zipTreeGrid.removeColumn(zipTreeGrid.getColumnByKey("name"));
        }

        TreeDataGrid.Column<ZipFileDto> nameColumn =
                zipTreeGrid.addComponentHierarchyColumn(this::renderItem);

        nameColumn.setHeader("Tên");
        nameColumn.setFlexGrow(1);
        nameColumn.setResizable(true);

        zipTreeGrid.setColumnPosition(nameColumn, 0);
    }

    private Component renderItem(ZipFileDto item) {

        HorizontalLayout hboxMain = uiComponents.create(HorizontalLayout.class);
        hboxMain.setAlignItems(FlexComponent.Alignment.CENTER);
        hboxMain.setWidthFull();
        hboxMain.setSpacing(true);
        hboxMain.setPadding(false);

        Icon icon = uiComponents.create(Icon.class);

        if (Boolean.TRUE.equals(item.getFolder())) {
            icon.setIcon(VaadinIcon.FOLDER);
            icon.addClassName("file-icon");
            icon.addClassName("folder"); // màu vàng
        } else {
            VaadinIcon vaadinIcon = pickIcon(item);
            icon.setIcon(vaadinIcon);
            icon.addClassName("file-icon");
            icon.addClassName(fileTypeClass(item));
        }

        icon.getElement().getStyle().set("flex-shrink", "0");

        Span span = uiComponents.create(Span.class);
        span.setText(item.getName());
        span.addClassName("folder-text");
        hboxMain.add(icon, span);
        hboxMain.addClickListener(event -> {
            zipTreeGrid.select(item);
            zipTreeDc.setItem(item);
        });

        return hboxMain;
    }

    private VaadinIcon pickIcon(ZipFileDto dto) {
        String ext = getExtension(dto.getName());
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> VaadinIcon.PICTURE;
            case "pdf" -> VaadinIcon.FILE_TEXT;
            case "xls", "xlsx" -> VaadinIcon.FILE_TABLE;
            case "doc", "docx" -> VaadinIcon.FILE_TEXT_O;
            case "zip", "rar", "7z" -> VaadinIcon.ARCHIVE;
            case "mp3", "wav", "flac" -> VaadinIcon.MUSIC;
            case "mp4", "avi", "mkv", "mov" -> VaadinIcon.FILM;
            case "txt" -> VaadinIcon.FILE_TEXT_O;
            default -> VaadinIcon.FILE_O;
        };
    }

    private String fileTypeClass(ZipFileDto dto) {
        String ext = getExtension(dto.getName());
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg" -> "ext-image";
            case "pdf" -> "ext-pdf";
            case "xls", "xlsx" -> "ext-excel";
            case "doc", "docx" -> "ext-word";
            case "ppt", "pptx" -> "ext-powerpoint";
            case "zip", "rar", "7z" -> "ext-archive";
            case "mp3", "wav", "flac" -> "ext-audio";
            case "mp4", "avi", "mkv", "mov" -> "ext-video";
            case "txt" -> "ext-text";
            default -> "ext-file";
        };
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }


    @Subscribe("zipTreeGrid.previewFileAction")
    public void onZipTreeGridPreviewFileAction(final ActionPerformedEvent event) {

        ZipFileDto selected = zipTreeGrid.getSingleSelectedItem();
        if (selected == null) return;
        String extension = getExtension(selected.getName());

        if (extension.startsWith("pdf")) {
            previewPdfFile(inputFile);
        } else if (extension.startsWith("txt") || extension.startsWith("docx")) {
            previewTextFile(inputFile);
        } else if (extension.startsWith("jpg")
                || extension.startsWith("png")
                || extension.startsWith("jpeg")
                || extension.startsWith("webp")
                || extension.startsWith("svg")
                || extension.startsWith("gif")) {
            previewImageFile(inputFile);
        } else if (extension.startsWith("mp4")
                || extension.startsWith("mov")
                || extension.startsWith("webm")) {
            preViewVideoFile(inputFile);
        } else if (extension.startsWith("html")
                || extension.startsWith("htm")
                || extension.startsWith("java")
                || extension.startsWith("js")
                || extension.startsWith("css")
                || extension.startsWith("md")
                || extension.startsWith("xml")
                || extension.startsWith("sql")) {
            preViewHtmlFile(inputFile);
        } else if (extension.startsWith("xlsx")) {
            previewExcelFile(inputFile);
        } else if (extension.startsWith("zip")) {
            previewZipFile(inputFile);
        } else {
            notifications.create("Loại file này chưa được hỗ trợ xem trước: " + extension)
                    .withType(Notifications.Type.WARNING)
                    .withCloseable(false)
                    .withDuration(2000)
                    .show();
        }
    }

    private void previewPdfFile(FileRef fileRelf) {
        DialogWindow<PdfPreview> window = dialogWindows.view(this, PdfPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewTextFile(FileRef fileRelf) {
        DialogWindow<TextPreview> window = dialogWindows.view(this, TextPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewImageFile(FileRef fileRelf) {
        DialogWindow<ImagePreview> window = dialogWindows.view(this, ImagePreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void preViewVideoFile(FileRef fileRelf) {
        DialogWindow<VideoPreview> window = dialogWindows.view(this, VideoPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void preViewHtmlFile(FileRef fileRelf) {
        DialogWindow<CodePreview> window = dialogWindows.view(this, CodePreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewExcelFile(FileRef fileRelf) {
        DialogWindow<ExcelPreview> window = dialogWindows.view(this, ExcelPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }

    private void previewZipFile(FileRef fileRelf) {
        DialogWindow<ZipPreview> window = dialogWindows.view(this, ZipPreview.class).build();
        window.getView().setInputFile(fileRelf);
        window.setResizable(true);
        window.open();
    }


}
