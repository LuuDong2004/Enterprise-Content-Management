package com.vn.ecm.view.folder;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.ecm.zipfile.ZipFolderService;
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

@Route(value = "folder-zip-dialog-view", layout = MainView.class)
@ViewController(id = "FolderZipDialogView")
@ViewDescriptor(path = "folder-zip-dialog-view.xml")
public class FolderZipDialogView extends StandardView {

    @ViewComponent
    private JmixPasswordField zipPasswordField;

    @ViewComponent
    private TypedTextField<String> zipFileNameField; // dùng String cho chuẩn

    @Autowired
    private Notifications notifications;

    @Autowired
    private ZipFolderService zipFolderService;

    @Autowired
    private Downloader downloader;

    private Folder folder;

    public void setFolder(Folder folder) {
        this.folder = folder;
    }
    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        if (folder == null || folder.getName() == null) {
            notifications.create("Thư mục không hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            close(StandardOutcome.CLOSE);
            return;
        }

        String defaultName = folder.getName();
        if (!defaultName.toLowerCase().endsWith(".zip")) {
            defaultName = defaultName + ".zip";
        }
        zipFileNameField.setValue(defaultName);
    }

    @Subscribe(id = "okBtn", subject = "clickListener")
    public void onOkBtnClick(final ClickEvent<JmixButton> event) {
        if (folder == null) {
            notifications.create("Thư mục không hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            close(StandardOutcome.CLOSE);
            return;
        }

        String fileName = trimToNull(zipFileNameField.getValue());
        String zipPassword = trimToNull(zipPasswordField.getValue());

        if (fileName == null) {
            fileName = folder.getName() + ".zip";
        }
        if (!fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName + ".zip";
        }

        try {
            byte[] zipBytes = zipFolderService.zipFolder(folder, fileName, zipPassword);
            downloader.download(zipBytes, fileName, DownloadFormat.ZIP);

            notifications.create("Đã nén thư mục thành ZIP: " + fileName)
                    .withType(Notifications.Type.SUCCESS)
                    .show();
            close(StandardOutcome.CLOSE);
        } catch (Exception e) {
            e.printStackTrace();
            notifications.create("Lỗi khi nén thư mục: " + e.getMessage())
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
}
