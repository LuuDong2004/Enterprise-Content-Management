package com.vn.ecm.view.confirmreplacedialog;


import com.vaadin.flow.router.Route;
import com.vn.ecm.view.main.MainView;
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

@Route(value = "confirm-replace-dialog", layout = MainView.class)
@ViewController(id = "ConfirmReplaceDialog")
@ViewDescriptor(path = "confirm-replace-dialog.xml")
public class ConfirmReplaceDialog extends StandardView {

    @Autowired
    private PermissionService permissionService;

    @ViewComponent
    private Span messageSpan;

    private String objectName;
    private User user;
    private ResourceRoleEntity role;
    private Integer permissionMask;
    private Folder targetFolder;
    private File targetFile;

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
        this.user = user;
        this.role = null;
    }

    public void setRole(ResourceRoleEntity role) {
        this.role = role;
        this.user = null;
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

        // Chỉ replace nếu target là Folder (File không có children)
        if (targetFolder != null) {
            if (user != null) {
                permissionService.replaceChildPermissions(user, targetFolder, permissionMask);
            } else if (role != null) {
                permissionService.replaceChildPermissions(role, targetFolder, permissionMask);
            }
        }
        // Nếu là File thì không làm gì (File không có children)

        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "noBtn", subject = "clickListener")
    public void onNoBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}