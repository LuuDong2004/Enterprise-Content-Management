package com.vn.ecm.view.sharecontent;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.ShareLink;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.ShareService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Route(value = "share-content-view", layout = MainView.class)
@ViewController(id = "ShareContentView")
@ViewDescriptor(path = "share-content-view.xml")
public class ShareContentView extends StandardView {

    @ViewComponent
    private TypedTextField<String> recipientEmailField;
    @ViewComponent
    private TypedTextField<String> messageField;
    @ViewComponent
    private TypedDatePicker<LocalDate> expiryDatePicker;
    @ViewComponent
    private TypedTextField<String> shareLinkField;
    @ViewComponent
    private Span itemNameLabel;
    @ViewComponent
    private JmixButton generateLinkBtn;
    @ViewComponent
    private JmixButton copyLinkBtn;
    @ViewComponent
    private JmixButton sendEmailBtn;

    @Autowired
    private ShareService shareService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Notifications notifications;

    private Folder targetFolder;
    private FileDescriptor targetFile;

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
        updateUI();
    }

    public void setTargetFile(FileDescriptor file) {
        this.targetFile = file;
        this.targetFolder = null;
        updateUI();
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        updateUI();
    }

    private void updateUI() {
        if (targetFolder != null) {
            itemNameLabel.setText("Thư mục: " + targetFolder.getName());
        } else if (targetFile != null) {
            itemNameLabel.setText("File: " + targetFile.getName());
        } else {
            itemNameLabel.setText("Chưa chọn file hoặc thư mục");
        }
        shareLinkField.setValue("");
        copyLinkBtn.setEnabled(false);
        sendEmailBtn.setEnabled(false);
    }

    @Subscribe("generateLinkBtn")
    public void onGenerateLinkBtnClick(ClickEvent<Button> event) {
        if (targetFolder == null && targetFile == null) {
            notifications.create("Vui lòng chọn file hoặc thư mục để chia sẻ")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        String recipientEmail = recipientEmailField.getValue();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            notifications.create("Vui lòng nhập email người nhận")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            LocalDateTime expiryDate = null;
            if (expiryDatePicker.getValue() != null) {
                expiryDate = expiryDatePicker.getValue()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .plusDays(1)
                        .minusSeconds(1); // End of day
            }

            String message = messageField.getValue();

            ShareLink shareLink = shareService.createShareLink(
                    targetFolder,
                    targetFile,
                    recipientEmail,
                    expiryDate,
                    message);

            String shareUrl = shareService.generateShareUrl(shareLink);
            shareLinkField.setValue(shareUrl);
            copyLinkBtn.setEnabled(true);
            sendEmailBtn.setEnabled(true);

            notifications.create("Đã tạo link chia sẻ thành công!")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (Exception e) {
            notifications.create("Lỗi: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }

    @Subscribe("copyLinkBtn")
    public void onCopyLinkBtnClick(ClickEvent<Button> event) {
        String link = shareLinkField.getValue();
        if (link != null && !link.isBlank()) {
            getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", link));
            notifications.create("Đã sao chép link vào clipboard!")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        }
    }

    @Subscribe("sendEmailBtn")
    public void onSendEmailBtnClick(ClickEvent<Button> event) {
        String link = shareLinkField.getValue();
        if (link == null || link.isBlank()) {
            notifications.create("Vui lòng tạo link trước khi gửi email")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        String recipientEmail = recipientEmailField.getValue();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            notifications.create("Vui lòng nhập email người nhận")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            // Tìm ShareLink từ token trong URL
            String token = link.substring(link.lastIndexOf("/") + 1);
            ShareLink shareLink = shareService.getShareLinkByToken(token);

            if (shareLink == null) {
                notifications.create("Không tìm thấy link chia sẻ")
                        .withType(Notifications.Type.ERROR)
                        .show();
                return;
            }

            shareService.sendShareEmail(shareLink, recipientEmail);
            notifications.create("Đã gửi email thành công!")
                    .withType(Notifications.Type.SUCCESS)
                    .show();
        } catch (Exception e) {
            notifications.create("Lỗi gửi email: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }
}