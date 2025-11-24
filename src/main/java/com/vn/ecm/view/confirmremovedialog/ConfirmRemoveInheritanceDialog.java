package com.vn.ecm.view.confirmremovedialog;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "confirm-remove-inheritance-dialog", layout = MainView.class)
@ViewController(id = "ConfirmRemoveInheritanceDialog")
@ViewDescriptor(path = "confirm-remove-inheritance-dialog.xml")
public class ConfirmRemoveInheritanceDialog extends StandardView {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DataManager dataManager;

    @ViewComponent
    private Span messageSpan;

    private User targetUser;
    private ResourceRoleEntity targetRole;
    private Folder targetFolder;
    private FileDescriptor targetFile;

    public void setTargetUser(User user) {
        this.targetUser = user;
        this.targetRole = null;
    }

    public void setTargetRole(ResourceRoleEntity role) {
        this.targetRole = role;
        this.targetUser = null;
    }

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
    }

    public void setTargetFile(FileDescriptor file) {
        this.targetFile = file;
        this.targetFolder = null;
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (messageSpan != null) {
            String objectName = targetFolder != null ? targetFolder.getName()
                    : (targetFile != null ? targetFile.getName() : "đối tượng này");
            messageSpan
                    .setText("Bạn có chắc chắn muốn xóa hoàn toàn tất cả các quyền kế thừa khỏi " + objectName + "? " +
                            "Thao tác này không thể hoàn tác.");
        }
    }

    @Subscribe(id = "yesBtn", subject = "clickListener")
    public void onYesBtnClick(final ClickEvent<JmixButton> event) {
        // Thực hiện xóa hoàn toàn
        if (targetUser != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetUser, targetFolder, false);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetUser, targetFile, false);
            }
        } else if (targetRole != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(targetRole, targetFolder, false);
            } else if (targetFile != null) {
                permissionService.disableInheritance(targetRole, targetFile, false);
            }
        }
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "noBtn", subject = "clickListener")
    public void onNoBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}
