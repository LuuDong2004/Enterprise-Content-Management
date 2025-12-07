package com.vn.ecm.view.file;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.ecm.zipfile.ZipFileService;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.textfield.JmixPasswordField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Iterator;

@Route(value = "file-zip-dialog-view", layout = MainView.class)
@ViewController(id = "FileZipDialogView")
@ViewDescriptor(path = "file-zip-dialog-view.xml")
public class FileZipDialogView extends StandardView {

    @ViewComponent
    private TypedTextField<String> zipFileNameField;

    @ViewComponent
    private JmixPasswordField zipPasswordField;

    private Folder folder;

    @Autowired
    private ZipFileService zipFileService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Downloader downloader;

    private Collection<FileDescriptor> fileDescriptors;

    public void setFolder(Folder folder) {
        this.folder = folder;
    }

    public void setFileDescriptors(Collection<FileDescriptor> fileDescriptors) {
        this.fileDescriptors = fileDescriptors;
    }
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (fileDescriptors == null || fileDescriptors.isEmpty()) {
            zipFileNameField.clear();
            return;
        }

        String defaultZipName;

        if (fileDescriptors.size() == 1) {
            Iterator<FileDescriptor> iterator = fileDescriptors.iterator();
            FileDescriptor fd = iterator.hasNext() ? iterator.next() : null;
            String baseName = getName(fd);
            defaultZipName = (baseName != null ? baseName : "file") + ".zip";
        } else {
            if (folder != null
                    && folder.getName() != null
                    && !folder.getName().isBlank()) {
                defaultZipName = folder.getName() + ".zip";
            } else {
                defaultZipName = "files.zip";
            }
        }

        zipFileNameField.setValue(defaultZipName);
    }

    @Subscribe(id = "compressBtn", subject = "clickListener")
    public void onCompressBtnClick(final ClickEvent<JmixButton> event) {
        if (fileDescriptors == null || fileDescriptors.isEmpty()) {
            notifications.create("Không có tệp nào được chọn để nén.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            close(StandardOutcome.CLOSE);
            return;
        }

        String fileName = trimToNull(zipFileNameField.getValue());
        String zipPassword = trimToNull(zipPasswordField.getValue());

        // Nếu user không nhập tên file → tự build
        if (fileName == null) {
            if (fileDescriptors.size() == 1) {
                FileDescriptor fd = fileDescriptors.iterator().next();
                String baseName = getName(fd);
                fileName = (baseName != null ? baseName : "file") + ".zip";
            } else if (folder != null && folder.getName() != null) {
                fileName = folder.getName() + ".zip";
            } else {
                fileName = "tep.zip";
            }
        }

        // Ensure có đuôi .zip
        if (!fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName + ".zip";
        }

        try {
            byte[] zipBytes = zipFileService.zipFiles(folder, fileDescriptors, fileName, zipPassword);

            downloader.download(zipBytes, fileName, DownloadFormat.ZIP);

            notifications.create("Đã nén " + fileDescriptors.size() + " tệp thành: " + fileName)
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            close(StandardOutcome.CLOSE);
        } catch (Exception e) {
            e.printStackTrace();
            notifications.create("Lỗi khi nén tệp: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }

    @Nullable
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Lấy tên file không gồm phần mở rộng
     */
    @Nullable
    private String getName(@Nullable FileDescriptor fd) {
        if (fd == null || fd.getName() == null) {
            return null;
        }

        String original = fd.getName();
        int dotIndex = original.lastIndexOf('.');

        return dotIndex > 0 ? original.substring(0, dotIndex) : original;
    }
}
