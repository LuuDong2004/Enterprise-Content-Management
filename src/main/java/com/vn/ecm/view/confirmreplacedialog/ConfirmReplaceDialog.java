package com.vn.ecm.view.confirmreplacedialog;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.PermissionService;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Route(value = "confirm-replace-dialog", layout = MainView.class)
@ViewController(id = "ConfirmReplaceDialog")
@ViewDescriptor(path = "confirm-replace-dialog.xml")
public class ConfirmReplaceDialog extends StandardView {

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private Span messageSpan;

    private String objectName;
    private Integer permissionMask;
    private Folder targetFolder;
    private File targetFile;

    private UUID userId;  // Thay đổi: lưu ID thay vì entity
    private String roleCode;
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
    }

    public void setTargetFile(File file) {
        this.targetFile = file;
        this.targetFolder = null;
    }

    public void setUser(User user) {
        System.out.println("DEBUG ConfirmReplaceDialog.setUser called with: " + user);
        this.userId = user != null ? user.getId() : null;
        System.out.println("DEBUG ConfirmReplaceDialog.setUser set userId to: " + this.userId);
        this.roleCode = null;
    }

    public void setRole(ResourceRoleEntity role) {
        System.out.println("DEBUG ConfirmReplaceDialog.setRole called with: " + role);
        this.roleCode = role != null ? role.getCode() : null;
        System.out.println("DEBUG ConfirmReplaceDialog.setRole set roleCode to: " + this.roleCode);
        this.userId = null;
    }

    public void setPermissionMask(Integer mask) {
        this.permissionMask = mask;
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (messageSpan != null && objectName != null) {
            messageSpan.setText("Do you want to replace all child object permissions of \""
                    + objectName + "\" with inheritable permissions from this object?");
        }
    }

    @Subscribe(id = "yesBtn", subject = "clickListener")
    public void onYesBtnClick(final ClickEvent<JmixButton> event) {
        if (permissionMask == null) {
            close(StandardOutcome.CLOSE);
            return;
        }
        // Chỉ replace nếu target là Folder
        if (targetFolder != null) {
            if (userId != null) {
                // Reload User từ DB
                User user = dataManager.load(User.class).id(userId).one();
                permissionService.replaceChildPermissions(user, targetFolder, permissionMask);
            } else if (roleCode != null) {
                // Reload Role từ DB
                ResourceRoleEntity role = permissionService.loadRoleByCode(roleCode);
                permissionService.replaceChildPermissions(role, targetFolder, permissionMask);
            }
        }
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "noBtn", subject = "clickListener")
    public void onNoBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}