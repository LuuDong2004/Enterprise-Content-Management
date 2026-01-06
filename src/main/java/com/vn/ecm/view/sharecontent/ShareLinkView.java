package com.vn.ecm.view.sharecontent;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.ShareLink;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.ShareService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "share/:token", layout = MainView.class)
@ViewController(id = "ShareLinkView")
@ViewDescriptor("share-link-view.xml")
public class ShareLinkView extends StandardView {

    @ViewComponent
    private Span itemNameLabel;
    @ViewComponent
    private Span ownerLabel;
    @ViewComponent
    private Span permissionLabel;
    @ViewComponent
    private JmixRadioButtonGroup<String> requestPermissionField;
    @ViewComponent
    private JmixTextArea requestMessageField;
    @ViewComponent
    private JmixButton sendRequestBtn;
    @ViewComponent
    private TypedTextField<String> requesterEmailField;

    @Autowired
    private ShareService shareService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private Notifications notifications;

    private ShareLink shareLink;
    private String token;

    @Subscribe
    public void onInit(InitEvent event) {
        // Khởi tạo items cho radioButtonGroup
        if (requestPermissionField != null) {
            requestPermissionField.setItems("READ", "MODIFY", "FULL");
            requestPermissionField.setItemLabelGenerator(item -> {
                switch (item) {
                    case "READ":
                        return "Chỉ xem";
                    case "MODIFY":
                        return "Chỉnh sửa";
                    case "FULL":
                        return "Toàn quyền";
                    default:
                        return item;
                }
            });
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getRouteParameters().get("token").orElse(null);
        if (token == null) {
            notifications.create("Link chia sẻ không hợp lệ")
                    .withType(Notifications.Type.ERROR)
                    .show();
            event.rerouteTo(MainView.class);
            return;
        }

        try {
            shareLink = shareService.validateAndAccess(token);
            if (shareLink == null) {
                notifications.create("Link chia sẻ không tồn tại hoặc đã hết hạn")
                        .withType(Notifications.Type.ERROR)
                        .show();
                event.rerouteTo(MainView.class);
                return;
            }
            updateUI();
        } catch (Exception e) {
            notifications.create("Lỗi: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
            event.rerouteTo(MainView.class);
        }
    }

    private void updateUI() {
        if (shareLink == null) {
            return;
        }

        // Kiểm tra null cho itemName
        String itemName = "Không xác định";
        if (shareLink.getFolder() != null && shareLink.getFolder().getName() != null) {
            itemName = shareLink.getFolder().getName();
        } else if (shareLink.getFile() != null && shareLink.getFile().getName() != null) {
            itemName = shareLink.getFile().getName();
        }

        if (itemNameLabel != null) {
            itemNameLabel.setText("Đang xem: " + itemName);
        }

        // Kiểm tra null cho owner
        String ownerName = "Không xác định";
        if (shareLink.getOwner() != null && shareLink.getOwner().getDisplayName() != null) {
            ownerName = shareLink.getOwner().getDisplayName();
        }

        if (ownerLabel != null) {
            ownerLabel.setText("Được chia sẻ bởi: " + ownerName);
        }

        // Kiểm tra null cho permissionType
        String permissionText = "Quyền hiện tại: ";
        String permissionType = shareLink.getPermissionType();
        if (permissionType != null) {
            switch (permissionType) {
                case "READ":
                    permissionText += "Chỉ xem";
                    break;
                case "MODIFY":
                    permissionText += "Chỉnh sửa";
                    break;
                case "FULL":
                    permissionText += "Toàn quyền";
                    break;
                default:
                    permissionText += "Chỉ xem";
                    break;
            }
        } else {
            permissionText += "Chỉ xem";
        }

        if (permissionLabel != null) {
            permissionLabel.setText(permissionText);
        }

        // Set giá trị mặc định cho requestPermissionField
        if (requestPermissionField != null) {
            try {
                requestPermissionField.setValue("READ");
            } catch (Exception e) {
                System.err.println("Cannot set permission field value: " + e.getMessage());
            }
        }

        // Set email cho requester
        if (requesterEmailField != null) {
            try {
                User currentUser = (User) currentAuthentication.getUser();
                String email = "";
                if (currentUser != null && currentUser.getEmail() != null) {
                    email = currentUser.getEmail();
                }
                requesterEmailField.setValue(email);
            } catch (Exception e) {
                System.err.println("Cannot set email field value: " + e.getMessage());
            }
        }
    }

    @Subscribe("sendRequestBtn")
    public void onSendRequestBtnClick(ClickEvent<Button> event) {
        User currentUser = (User) currentAuthentication.getUser();
        if (currentUser == null) {
            notifications.create("Vui lòng đăng nhập để gửi yêu cầu")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        String requestedPermission = requestPermissionField.getValue();
        if (requestedPermission == null || requestedPermission.isBlank()) {
            requestedPermission = "READ";
        }

        String requesterEmail = requesterEmailField != null ? requesterEmailField.getValue() : null;
        if (requesterEmail == null || requesterEmail.isBlank()) {
            requesterEmail = currentUser.getEmail();
        }

        if (requesterEmail == null || requesterEmail.isBlank()) {
            notifications.create("Vui lòng nhập email để chủ sở hữu có thể cấp quyền.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        try {
            String message = requestMessageField.getValue();
            shareService.createPermissionRequest(
                    shareLink,
                    requesterEmail,
                    requestedPermission,
                    message);

            notifications.create("Đã gửi yêu cầu truy cập. Chủ sở hữu sẽ xem xét và cấp quyền.")
                    .withType(Notifications.Type.SUCCESS)
                    .show();

            sendRequestBtn.setEnabled(false);
        } catch (Exception e) {
            notifications.create("Lỗi: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
    }
}