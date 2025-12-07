package com.vn.ecm.view.folder;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;

import com.vn.ecm.service.ecm.zipfile.ZipFileService;
import com.vn.ecm.service.ecm.zipfile.ZipFolderService;
import io.jmix.flowui.Notifications;

import io.jmix.flowui.UiComponents;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class CreateFolderZipAction {
    private final ZipFileService zipFileService;
    @Autowired
    private ZipFolderService zipFolderService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Downloader downloader;

    @Autowired
    private UiComponents uiComponents;

    public CreateFolderZipAction(ZipFileService zipFileService) {
        this.zipFileService = zipFileService;
    }

    public void openZipFolderDialog(Folder folder) {
        if (folder == null) {
            notifications.create("Thư mục không hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nén thư mục thành ZIP");

        TextField zipFileNameField = uiComponents.create(TextField.class);
        zipFileNameField.setLabel("Tên tệp ZIP");
        zipFileNameField.setWidthFull();
        zipFileNameField.setValue(folder.getName() + ".zip");

        PasswordField zipPasswordField = uiComponents.create(PasswordField.class);
        zipPasswordField.setLabel("Mật khẩu (tùy chọn)");
        zipPasswordField.setWidthFull();

        Button compressButton = uiComponents.create(Button.class);
        compressButton.setText("Nén và tải xuống");

        compressButton.addClickListener(clickEvent -> {
            String zipFileName = zipFileNameField.getValue();
            String zipPassword = zipPasswordField.getValue();

            if (zipFileName == null || zipFileName.isBlank()) {
                zipFileName = folder.getName() + ".zip";
            }
            if (!zipFileName.toLowerCase().endsWith(".zip")) {
                zipFileName = zipFileName + ".zip";
            }

            try {
                byte[] zipBytes = zipFolderService.zipFolder(folder, zipFileName, zipPassword);

                downloader.download(zipBytes, zipFileName, DownloadFormat.ZIP);

                notifications.create("Đã nén thư mục thành ZIP: " + zipFileName)
                        .withType(Notifications.Type.SUCCESS)
                        .show();

                dialog.close();
            } catch (Exception exception) {
                exception.printStackTrace();
                notifications.create("Lỗi khi nén thư mục: " + exception.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        });

        Button cancelButton = uiComponents.create(Button.class);
        cancelButton.setText("Hủy");
        cancelButton.addClickListener(clickEvent -> dialog.close());

        VerticalLayout layout = uiComponents.create(VerticalLayout.class);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.add(zipFileNameField, zipPasswordField, compressButton, cancelButton);

        dialog.add(layout);
        dialog.setWidth("400px");
        dialog.open();
    }

    public void openZipFilesDialog(Folder folder,
                                   Collection<FileDescriptor> filesToCompress) {

        if (filesToCompress == null || filesToCompress.isEmpty()) {
            notifications.create("Không có tệp nào được chọn để nén.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nén các tệp đã chọn");

        // Gợi ý tên ZIP
        String defaultZipName;
        if (filesToCompress.size() == 1) {
            FileDescriptor fd = filesToCompress.iterator().next();
            String originalName = fd.getName();
            int dotIndex = originalName.lastIndexOf('.');
            String baseName = (dotIndex > 0) ? originalName.substring(0, dotIndex) : originalName;
            defaultZipName = baseName + ".zip";
        } else {
            if (folder != null && folder.getName() != null && !folder.getName().isBlank()) {
                defaultZipName = folder.getName() + ".zip";
            } else {
                defaultZipName = "files.zip";
            }
        }

        TextField zipFileNameField = uiComponents.create(TextField.class);
        zipFileNameField.setLabel("Tên tệp ZIP");
        zipFileNameField.setWidthFull();
        zipFileNameField.setValue(defaultZipName);

        PasswordField zipPasswordField = uiComponents.create(PasswordField.class);
        zipPasswordField.setLabel("Mật khẩu (tùy chọn)");
        zipPasswordField.setWidthFull();

        Button compressButton = uiComponents.create(Button.class);
        compressButton.setText("Nén và tải xuống");

        compressButton.addClickListener(clickEvent -> {
            String zipFileName = zipFileNameField.getValue();
            String zipPassword = zipPasswordField.getValue();

            if (zipFileName == null || zipFileName.isBlank()) {
                zipFileName = defaultZipName;
            }
            if (!zipFileName.toLowerCase().endsWith(".zip")) {
                zipFileName = zipFileName + ".zip";
            }

            try {
                byte[] zipBytes =
                        zipFileService.zipFiles(folder,filesToCompress, zipFileName, zipPassword);

                downloader.download(zipBytes, zipFileName, DownloadFormat.ZIP);

                notifications.create("Đã nén " + filesToCompress.size() + " tệp thành: " + zipFileName)
                        .withType(Notifications.Type.SUCCESS)
                        .show();

                dialog.close();
            } catch (Exception e) {
                e.printStackTrace();
                notifications.create("Lỗi khi nén tệp: " + e.getMessage())
                        .withType(Notifications.Type.ERROR)
                        .show();
            }
        });

        Button cancelButton = uiComponents.create(Button.class);
        cancelButton.setText("Hủy");
        cancelButton.addClickListener(clickEvent -> dialog.close());

        VerticalLayout layout = uiComponents.create(VerticalLayout.class);
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.add(zipFileNameField, zipPasswordField, compressButton, cancelButton);

        dialog.add(layout);
        dialog.setWidth("420px");
        dialog.open();
    }
}
