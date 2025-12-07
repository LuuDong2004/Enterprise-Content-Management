package com.vn.ecm.view.folder;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;

import com.vn.ecm.service.ecm.zipfile.ZipFolderService;
import io.jmix.core.FileRef;
import io.jmix.flowui.Notifications;

import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class CreateFolderZipAction {
    @Autowired
    private ZipFolderService zipFolderService;
    @Autowired
    private Notifications notifications;

    public void openZipFolderDialog(Folder folder,
                                    Consumer<FileDescriptor> onSuccess) {

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nén thư mục thành ZIP");

        TextField zipFileNameField = new TextField("Tên file ZIP");
        zipFileNameField.setWidthFull();
        zipFileNameField.setValue(folder.getName() + ".zip");

        PasswordField zipPasswordField = new PasswordField("Mật khẩu (tùy chọn)");
        zipPasswordField.setWidthFull();

        Button compressButton = new Button("Nén", clickEvent -> {
            String zipFileName = zipFileNameField.getValue();
            String zipPassword = zipPasswordField.getValue();

            try {
                // 👉 Bây giờ zipFolder trả về FileDescriptor
                FileDescriptor zipFileDescriptor =
                        zipFolderService.zipFolder(folder, zipFileName, zipPassword);

                // Gọi callback để view tự cập nhật UI (filesDc, reload, ...)
                if (onSuccess != null) {
                    onSuccess.accept(zipFileDescriptor);
                }

                notifications.create("Đã tạo file ZIP: " + zipFileDescriptor.getName())
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

        Button cancelButton = new Button("Hủy", clickEvent -> dialog.close());

        VerticalLayout layout = new VerticalLayout(
                zipFileNameField,
                zipPasswordField,
                compressButton,
                cancelButton
        );
        layout.setPadding(false);
        layout.setSpacing(true);

        dialog.add(layout);
        dialog.setWidth("400px");
        dialog.open();
    }
}
