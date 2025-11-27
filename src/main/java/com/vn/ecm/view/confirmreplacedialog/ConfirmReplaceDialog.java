package com.vn.ecm.view.confirmreplacedialog;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.User;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.*;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import com.vaadin.flow.component.UI;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Route(value = "confirm-replace-dialog", layout = MainView.class)
@ViewController(id = "ConfirmReplaceDialog")
@ViewDescriptor(path = "confirm-replace-dialog.xml")
public class ConfirmReplaceDialog extends StandardView {

    @ViewComponent
    private Notification notification;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private Notifications notifications;
    @ViewComponent
    private Span messageSpan;

    private String objectName;
    private Integer permissionMask;
    private Folder targetFolder;
    private FileDescriptor targetFile;

    private UUID userId; // Thay đổi: lưu ID thay vì entity
    private String roleCode;
    @ViewComponent
    private MessageBundle messageBundle;

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
    }

    public void setTargetFile(FileDescriptor file) {
        this.targetFile = file;
        this.targetFolder = null;
    }

    public void setUser(User user) {
        this.userId = user != null ? user.getId() : null;
        this.roleCode = null;
    }

    public void setRole(ResourceRoleEntity role) {
        this.roleCode = role != null ? role.getCode() : null;
        this.userId = null;
    }

    public void setPermissionMask(Integer mask) {
        this.permissionMask = mask;
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (messageSpan != null && objectName != null) {
            messageSpan.setText(
                    "Thao tác này sẽ thay thế tất cả các quyền được gán trực tiếp trên các đối tượng con bằng các quyền kế thừa từ đối tượng "
                            + objectName + ".");
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
            // Sử dụng BackgroundTask để replace permissions (có thể chạy lâu với subtree
            // lớn)
            ReplacePermissionsTask task = new ReplacePermissionsTask();
            dialogs.createBackgroundTaskDialog(task)
                    .withHeader("Thay thế quyền")
                    .withText("Đang thay thế quyền cho các đối tượng con...")
                    .withCancelAllowed(true)
                    .open();
        } else {
            close(StandardOutcome.SAVE);
        }
    }

    /**
     * BackgroundTask để thay thế quyền cho các đối tượng con.
     */
    private class ReplacePermissionsTask extends BackgroundTask<Integer, Void> {
        private final UI ui;

        public ReplacePermissionsTask() {
            super(30, TimeUnit.MINUTES, ConfirmReplaceDialog.this);
            this.ui = UI.getCurrent();
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(0);

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

            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(100);

            // Reload và đóng dialog trên UI thread
            if (ui != null) {
                ui.access(() -> {
                    notification.show(messageBundle.getMessage("confirm"));
                    close(StandardOutcome.SAVE);
                });
            }

            return null;
        }
    }

    @Subscribe(id = "noBtn", subject = "clickListener")
    public void onNoBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
}