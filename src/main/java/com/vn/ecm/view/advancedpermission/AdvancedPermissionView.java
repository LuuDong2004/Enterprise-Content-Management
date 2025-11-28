package com.vn.ecm.view.advancedpermission;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;

import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.view.blockinheritance.BlockInheritance;
import com.vn.ecm.view.blockinheritance.BlockInheritanceAction;
import com.vn.ecm.view.confirmreplacedialog.ConfirmReplaceDialog;
import com.vn.ecm.view.confirmremovedialog.ConfirmRemoveInheritanceDialog;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.Dialogs;
import com.vaadin.flow.component.UI;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Route(value = "advanced-permission-view", layout = MainView.class)
@ViewController(id = "AdvancedPermissionView")
@ViewDescriptor(path = "advanced-permission-view.xml")
public class AdvancedPermissionView extends StandardView {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private DialogWindows dialogWindows;

    @Autowired
    private Dialogs dialogs;

    @ViewComponent
    private DataGrid<Permission> permissionDataGrid;

    @ViewComponent
    private JmixButton disableInheritanceBtn;

    @ViewComponent
    private TextField pathArea;

    // Target có thể là Folder hoặc File
    private Folder targetFolder;
    private FileDescriptor targetFile;

    // Lưu action được chọn từ BlockInheritance dialog
    private BlockInheritanceAction pendingAction;
    private User pendingUser;
    private ResourceRoleEntity pendingRole;

    // Lưu snapshot của permissions trước khi xóa tạm thời (để rollback)
    private List<Permission> permissionsSnapshot;

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
    }

    public void setTargetFile(FileDescriptor file) {
        this.targetFile = file;
        this.targetFolder = null;
    }

    String path = "";

    public void setPath(String path) {
        this.path = path;
    }

    private void loadPermissionsForFolder(Folder folder) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.folder = :folder")
                .parameter("folder", folder)
                .list();

        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        container.setItems(permissions);
    }

    private void loadPermissionsForFile(FileDescriptor file) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.file = :file")
                .parameter("file", file)
                .list();

        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        container.setItems(permissions);
    }

    private void updateInheritanceButtonLabel(Permission permission) {
        if (permission == null) {
            CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
            if (!container.getItems().isEmpty()) {
                permission = container.getItems().get(0);
            }
        }
        if (permission == null) {
            // Không có permission => coi như inheritance đã bị remove
            disableInheritanceBtn.setText("Bật kế thừa");
            return;
        }
        if (Boolean.FALSE.equals(permission.getInheritEnabled())) {
            disableInheritanceBtn.setText("Bật kế thừa");
        } else {
            disableInheritanceBtn.setText("Ngắt kế thừa");
        }
    }

    @Subscribe
    public void onInit(InitEvent event) {
        permissionDataGrid.addColumn(permission -> {
            if (permission.getUser() != null) {
                return permission.getUser().getUsername();
            } else if (permission.getRoleCode() != null) {
                return permission.getRoleCode();
            }
            return "";
        }).setHeader("Đối tượng");

        permissionDataGrid.addColumn(
                permission -> "Cho phép").setHeader("Trạng thái");

        permissionDataGrid.addColumn(permission -> {
            if (permission.getPermissionMask() == null)
                return "";

            int mask = permission.getPermissionMask();

            if (PermissionType.hasPermission(mask, PermissionType.FULL)) {
                return PermissionType.FULL.toString();
            }

            StringBuilder sb = new StringBuilder();

            for (PermissionType type : PermissionType.values()) {
                if (PermissionType.hasPermission(mask, type)) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(type.toString());
                }
            }

            return sb.toString();
        }).setHeader("Quyền truy cập");

        permissionDataGrid.addColumn(
                permission -> {
                    if (permission.getInheritedFrom() == null)
                        return "";
                    return permission.getInheritedFrom();
                }).setHeader("Kế thừa từ");

        permissionDataGrid.addColumn(
                permission -> {
                    AppliesTo applies = permission.getAppliesTo();
                    if (applies == null)
                        return "";
                    switch (applies) {
                        case THIS_FOLDER_ONLY:
                            return "Chỉ thư mục";
                        case THIS_FOLDER_SUBFOLDERS_FILES:
                            return "Thư mục, các thư mục con, tệp";
                        case THIS_FOLDER_SUBFOLDERS:
                            return "Thư mục, các thư mục con";
                        case THIS_FOLDER_FILES:
                            return "Thư mục và tệp";
                        case SUBFOLDERS_FILES_ONLY:
                            return "Chỉ các thư mục con và tệp";
                        default:
                            return "";
                    }
                }).setHeader("Áp dụng cho");

        permissionDataGrid.addSelectionListener(selectionEvent -> {
            Permission selected = permissionDataGrid.getSingleSelectedItem();
            updateInheritanceButtonLabel(selected);
        });
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue("Đường dẫn: " + path);
        }
        if (targetFolder != null) {
            pathArea.setValue(permissionService.getFullPath(targetFolder));
            loadPermissionsForFolder(targetFolder);
        } else if (targetFile != null) {
            pathArea.setValue(permissionService.getFullPath(targetFile));
            loadPermissionsForFile(targetFile);
        }

        // Tự động chọn permission đầu tiên
        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        if (!container.getItems().isEmpty()) {
            permissionDataGrid.select(container.getItems().get(0));
        }

        // RELOAD PERMISSION TỪ DB TRƯỚC KHI UPDATE LABEL
        Permission selected = permissionDataGrid.getSingleSelectedItem();
        if (selected != null) {
            // Reload từ DB để đảm bảo có trạng thái mới nhất
            Permission freshPermission = null;
            if (selected.getUser() != null) {
                if (targetFolder != null) {
                    freshPermission = permissionService.loadPermission(selected.getUser(), targetFolder);
                } else if (targetFile != null) {
                    freshPermission = permissionService.loadPermission(selected.getUser(), targetFile);
                }
            } else if (selected.getRoleCode() != null) {
                ResourceRoleEntity role = permissionService.loadRoleByCode(selected.getRoleCode());
                if (role != null) {
                    if (targetFolder != null) {
                        freshPermission = permissionService.loadPermission(role, targetFolder);
                    } else if (targetFile != null) {
                        freshPermission = permissionService.loadPermission(role, targetFile);
                    }
                }
            }
            updateInheritanceButtonLabel(freshPermission != null ? freshPermission : selected);
        } else {
            updateInheritanceButtonLabel(null);
        }
    }

    @Subscribe(id = "disableInheritanceBtn", subject = "clickListener")
    public void onDisableInheritanceBtnClick(final ClickEvent<JmixButton> event) {
        Permission permission = permissionDataGrid.getSingleSelectedItem();
        if (permission == null) {
            EnableRemoveInheritanceTask task = new EnableRemoveInheritanceTask();
            dialogs.createBackgroundTaskDialog(task)
                    .withHeader("Bật kế thừa quyền")
                    .withText("Đang bật kế thừa quyền...")
                    .withCancelAllowed(true)
                    .open();
            return;
        }
        if (Boolean.TRUE.equals(permission.getInheritEnabled())) {
            // Đang kế thừa → disable → mở dialog Convert/Remove
            DialogWindow<BlockInheritance> window = dialogWindows.view(this, BlockInheritance.class).build();
            if (permission.getUser() != null) {
                if (targetFolder != null) {
                    window.getView().setTargetUserFolder(permission.getUser(), targetFolder);
                } else if (targetFile != null) {
                    window.getView().setTargetUserFile(permission.getUser(), targetFile);
                }
            } else if (permission.getRoleCode() != null) {
                ResourceRoleEntity role = permissionService.loadRoleByCode(permission.getRoleCode());
                if (targetFolder != null) {
                    window.getView().setTargetRoleFolder(role, targetFolder);
                } else if (targetFile != null) {
                    window.getView().setTargetRoleFile(role, targetFile);
                }
            }
            window.addAfterCloseListener(e -> {
                BlockInheritanceAction action = window.getView().getSelectedAction();
                if (e.closedWith(StandardOutcome.SAVE) && action != BlockInheritanceAction.CANCEL) {
                    // Lưu action và thông tin target
                    pendingAction = action;
                    pendingUser = window.getView().getTargetUser();
                    pendingRole = window.getView().getTargetRole();

                    // Lưu snapshot permissions hiện tại để rollback nếu cần
                    CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
                    permissionsSnapshot = new ArrayList<>(container.getItems());

                    // Nếu là REMOVE, xóa tạm thời permissions được kế thừa (chỉ trong UI, chưa commit DB)
                    if (action == BlockInheritanceAction.REMOVE) {
                        removeInheritedPermissionsTemporarily();
                        // Không reload từ DB, giữ UI state
                    } else if (action == BlockInheritanceAction.CONVERT) {
                        // Convert: sử dụng BackgroundTask vì có thể chạy lâu với subtree lớn
                        ConvertInheritanceTask task = new ConvertInheritanceTask();
                        dialogs.createBackgroundTaskDialog(task)
                                .withHeader("Chuyển đổi quyền")
                                .withText("Đang chuyển đổi quyền kế thừa thành quyền rõ ràng...")
                                .withCancelAllowed(true)
                                .open();
                    }
                } else {
                    // Cancel hoặc đóng dialog → reset
                    pendingAction = null;
                    pendingUser = null;
                    pendingRole = null;
                    permissionsSnapshot = null;
                    // Reload từ DB để đảm bảo hiển thị đúng
                    reloadPermissions();
                }
                updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
            });
            window.open();
        } else {
            // Đã disable → enable lại
            // Sử dụng BackgroundTask vì enableInheritance có thể chạy lâu với subtree lớn
            EnableInheritanceTask task = new EnableInheritanceTask(permission);
            dialogs.createBackgroundTaskDialog(task)
                    .withHeader("Bật kế thừa quyền")
                    .withText("Đang bật kế thừa quyền...")
                    .withCancelAllowed(true)
                    .open();
        }
    }

    @Subscribe(id = "applyBtn", subject = "clickListener")
    public void onApplyBtnClick(final ClickEvent<JmixButton> event) {
        Permission permission = permissionDataGrid.getSingleSelectedItem();
        if (permission == null) {
            return;
        }
        // Chỉ xử lý cho Folder
        if (targetFolder == null) {
            return;
        }
        // Lấy quyền hiện tại của folder cha từ DB
        Permission parentPermission = null;
        User reloadedUser = null;
        ResourceRoleEntity reloadedRole = null;

        if (permission.getUser() != null) {
            // RELOAD USER từ DB
            reloadedUser = dataManager.load(User.class)
                    .id(permission.getUser().getId())
                    .one();
            parentPermission = permissionService.loadPermission(reloadedUser, targetFolder);
        } else if (permission.getRoleCode() != null) {
            // RELOAD ROLE từ DB
            reloadedRole = permissionService.loadRoleByCode(permission.getRoleCode());
            if (reloadedRole != null) {
                parentPermission = permissionService.loadPermission(reloadedRole, targetFolder);
            }
        }

        if (parentPermission == null || parentPermission.getPermissionMask() == null) {
            return;
        }
        String objectName = targetFolder.getName();
        DialogWindow<ConfirmReplaceDialog> window = dialogWindows.view(this, ConfirmReplaceDialog.class).build();
        window.getView().setObjectName(objectName);
        window.getView().setTargetFolder(targetFolder);
        window.getView().setPermissionMask(parentPermission.getPermissionMask());

        if (reloadedUser != null) {
            window.getView().setUser(reloadedUser);
        } else if (reloadedRole != null) {
            window.getView().setRole(reloadedRole);
        }
        window.addAfterCloseListener(e -> {
            if (e.closedWith(StandardOutcome.SAVE)) {
                reloadPermissions();
            }
        });

        window.open();
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        // Nếu có pending action (đặc biệt là REMOVE), rollback
        if (pendingAction == BlockInheritanceAction.REMOVE && permissionsSnapshot != null) {
            // Rollback: khôi phục permissions từ snapshot
            CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
            container.setItems(new ArrayList<>(permissionsSnapshot));
        }

        // Reset pending state
        pendingAction = null;
        pendingUser = null;
        pendingRole = null;
        permissionsSnapshot = null;

        close(StandardOutcome.CLOSE);
    }

    private void reloadPermissions() {
        if (targetFolder != null) {
            loadPermissionsForFolder(targetFolder);
        } else if (targetFile != null) {
            loadPermissionsForFile(targetFile);
        }
    }

    @Subscribe(id = "okBtn", subject = "clickListener")
    public void onOkBtnClick(final ClickEvent<JmixButton> event) {
        // Nếu có pending action
        if (pendingAction == BlockInheritanceAction.REMOVE) {
            // Hiển thị dialog xác nhận xóa hoàn toàn
            DialogWindow<ConfirmRemoveInheritanceDialog> window = dialogWindows
                    .view(this, ConfirmRemoveInheritanceDialog.class).build();

            if (pendingUser != null) {
                window.getView().setTargetUser(pendingUser);
                if (targetFolder != null) {
                    window.getView().setTargetFolder(targetFolder);
                } else if (targetFile != null) {
                    window.getView().setTargetFile(targetFile);
                }
            } else if (pendingRole != null) {
                window.getView().setTargetRole(pendingRole);
                if (targetFolder != null) {
                    window.getView().setTargetFolder(targetFolder);
                } else if (targetFile != null) {
                    window.getView().setTargetFile(targetFile);
                }
            }

            window.addAfterCloseListener(e -> {
                if (e.closedWith(StandardOutcome.SAVE)) {
                    // FIX: Reload ngay lập tức trong UI thread
                    reloadPermissions();
                    updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());

                    // Reset pending state
                    pendingAction = null;
                    pendingUser = null;
                    pendingRole = null;
                    permissionsSnapshot = null;

                    // Đóng view sau khi đã reload
                    close(StandardOutcome.SAVE);
                } else {
                    // Cancel - rollback nếu cần
                    if (permissionsSnapshot != null) {
                        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
                        container.setItems(new ArrayList<>(permissionsSnapshot));
                    }
                    pendingAction = null;
                    pendingUser = null;
                    pendingRole = null;
                    permissionsSnapshot = null;
                }
            });

            window.open();
        } else {
            close(StandardOutcome.SAVE);
        }
    }

    private void removeInheritedPermissionsTemporarily() {
        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        List<Permission> toRemove = new ArrayList<>();

        for (Permission perm : container.getItems()) {
            if (!Boolean.TRUE.equals(perm.getInherited())) {
                continue;
            }

            boolean matchesPendingUser = pendingUser != null && perm.getUser() != null
                    && pendingUser.getId().equals(perm.getUser().getId());
            boolean matchesPendingRole = pendingRole != null && perm.getRoleCode() != null
                    && pendingRole.getCode().equals(perm.getRoleCode());

            if (matchesPendingUser || matchesPendingRole) {
                toRemove.add(perm);
            }
        }

        container.getMutableItems().removeAll(toRemove);
    }

    private void executeConvertAction() {
        if (pendingUser != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(pendingUser, targetFolder, true);
            } else if (targetFile != null) {
                permissionService.disableInheritance(pendingUser, targetFile, true);
            }
        } else if (pendingRole != null) {
            if (targetFolder != null) {
                permissionService.disableInheritance(pendingRole, targetFolder, true);
            } else if (targetFile != null) {
                permissionService.disableInheritance(pendingRole, targetFile, true);
            }
        }
        // Reset pending state
        pendingAction = null;
        pendingUser = null;
        pendingRole = null;
        permissionsSnapshot = null;
    }

    private class EnableRemoveInheritanceTask extends BackgroundTask<Integer, Void> {
        private final UI ui;

        public EnableRemoveInheritanceTask() {
            super(30, TimeUnit.MINUTES, AdvancedPermissionView.this);
            this.ui = UI.getCurrent();
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(0);

            if (targetFolder != null) {
                permissionService.enableRemoveInheritance(targetFolder);
            } else if (targetFile != null) {
                permissionService.enableRemoveInheritance(targetFile);
            }

            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(100);

            if (ui != null) {
                ui.access(() -> {
                    reloadPermissions();
                    updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
                });
            }

            return null;
        }
    }

    private class EnableInheritanceTask extends BackgroundTask<Integer, Void> {
        private final Permission permission;
        private final UI ui;

        public EnableInheritanceTask(Permission permission) {
            super(30, TimeUnit.MINUTES, AdvancedPermissionView.this);
            this.permission = permission;
            this.ui = UI.getCurrent();
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(0);

            if (permission.getUser() != null) {
                if (targetFolder != null) {
                    permissionService.enableInheritance(permission.getUser(), targetFolder);
                } else if (targetFile != null) {
                    permissionService.enableInheritance(permission.getUser(), targetFile);
                }
            } else if (permission.getRoleCode() != null) {
                ResourceRoleEntity role = permissionService.loadRoleByCode(permission.getRoleCode());
                if (role != null) {
                    if (targetFolder != null) {
                        permissionService.enableInheritance(role, targetFolder);
                    } else if (targetFile != null) {
                        permissionService.enableInheritance(role, targetFile);
                    }
                }
            }

            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(100);

            if (ui != null) {
                ui.access(() -> {
                    reloadPermissions();
                    updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
                });
            }

            return null;
        }
    }

    private class ConvertInheritanceTask extends BackgroundTask<Integer, Void> {
        private final UI ui;

        public ConvertInheritanceTask() {
            super(30, TimeUnit.MINUTES, AdvancedPermissionView.this);
            this.ui = UI.getCurrent();
        }

        @Override
        public Void run(TaskLifeCycle<Integer> taskLifeCycle) throws Exception {
            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(0);

            executeConvertAction();

            if (taskLifeCycle.isCancelled()) {
                return null;
            }

            taskLifeCycle.publish(100);

            if (ui != null) {
                ui.access(() -> {
                    reloadPermissions();
                    updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
                });
            }

            return null;
        }
    }
}