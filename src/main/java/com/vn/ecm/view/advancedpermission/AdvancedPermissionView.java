package com.vn.ecm.view.advancedpermission;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.PermissionService;
import com.vn.ecm.view.blockinheritance.BlockInheritance;
import com.vn.ecm.view.confirmreplacedialog.ConfirmReplaceDialog;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.data.ContainerDataUnit;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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

    @ViewComponent
    private DataGrid<Permission> permissionDataGrid;

    @ViewComponent
    private JmixButton disableInheritanceBtn;

    @ViewComponent
    private TextArea nameArea;

    @ViewComponent
    private CollectionLoader<Permission> permissionsDl;

    // Target có thể là Folder hoặc File
    private Folder targetFolder;
    private File targetFile;

    public void setTargetFolder(Folder folder) {
        this.targetFolder = folder;
        this.targetFile = null;
    }

    public void setTargetFile(File file) {
        this.targetFile = file;
        this.targetFolder = null;
    }

    String path = "";

    public void setPath(String path) {
        this.path = path;
    }

    private EcmObject target;
    public void setTarget(EcmObject target) {
        this.target = target;
    }

    private void loadPermissionsForFolder(Folder folder) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.folder = :folder")
                .parameter("folder", folder)
                .list();

        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        container.setItems(permissions);
    }

    private void loadPermissionsForFile(File file) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.file = :file")
                .parameter("file", file)
                .list();

        CollectionContainer<Permission> container = getViewData().getContainer("permissionsDc");
        container.setItems(permissions);
    }

    private void updateInheritanceButtonLabel(Permission permission) {
        if (permission == null && permissionDataGrid.getItems() != null) {
            if (permissionDataGrid.getItems() instanceof ContainerDataUnit) {
                ContainerDataUnit<Permission> dataUnit =
                        (ContainerDataUnit<Permission>) permissionDataGrid.getItems();
                if (dataUnit.getContainer() instanceof CollectionContainer) {
                    CollectionContainer<Permission> container =
                            (CollectionContainer<Permission>) dataUnit.getContainer();
                    if (!container.getItems().isEmpty()) {
                        permission = container.getItems().get(0);
                    }
                }
            }
        }
        if (permission == null) {
            // Không có permission => coi như inheritance đã bị remove
            disableInheritanceBtn.setText("Enable Inheritance");
            return;
        }

        if (Boolean.FALSE.equals(permission.getInheritEnabled())) {
            disableInheritanceBtn.setText("Enable Inheritance");
        } else {
            disableInheritanceBtn.setText("Disable Inheritance");
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
        }).setHeader("Principal");

        permissionDataGrid.addColumn(
                permission -> "Allow"
        ).setHeader("Type");

        permissionDataGrid.addColumn(permission -> {
            if (permission.getPermissionMask() == null) return "";

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
        }).setHeader("Access");

        permissionDataGrid.addColumn(
                permission -> {
                    if (permission.getInheritedFrom() == null) return "";
                    return permission.getInheritedFrom();
                }
        ).setHeader("Inherited from");

        permissionDataGrid.addColumn(
                permission -> {
                    AppliesTo applies = permission.getAppliesTo();
                    if (applies == null) return "";
                    switch (applies) {
                        case THIS_FOLDER_ONLY: return "This folder only";
                        case THIS_FOLDER_SUBFOLDERS_FILES: return "This folder, subfolders and files";
                        case THIS_FOLDER_SUBFOLDERS: return "This folder and subfolders";
                        case THIS_FOLDER_FILES: return "This folder and files";
                        case SUBFOLDERS_FILES_ONLY: return "Subfolders and files only";
                        default: return "";
                    }
                }
        ).setHeader("Applies to");

        permissionDataGrid.addSelectionListener(selectionEvent -> {
            Permission selected = permissionDataGrid.getSingleSelectedItem();
            updateInheritanceButtonLabel(selected);
        });
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (targetFolder != null) {
            nameArea.setValue(targetFolder.getName());
            loadPermissionsForFolder(targetFolder);
        } else if (targetFile != null) {
            nameArea.setValue(targetFile.getName());
            loadPermissionsForFile(targetFile);
        }

        // Tự động chọn permission đầu tiên
        if (permissionDataGrid.getItems() instanceof ContainerDataUnit) {
            ContainerDataUnit<Permission> dataUnit =
                    (ContainerDataUnit<Permission>) permissionDataGrid.getItems();
            if (dataUnit.getContainer() instanceof CollectionContainer) {
                CollectionContainer<Permission> container =
                        (CollectionContainer<Permission>) dataUnit.getContainer();
                if (!container.getItems().isEmpty()) {
                    permissionDataGrid.select(container.getItems().get(0));
                }
            }
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
            // Không có permission nào => enable inheritance (Remove case)
            if (targetFolder != null) {
                permissionService.enableRemoveInheritance(targetFolder);
            } else if (targetFile != null) {
                permissionService.enableRemoveInheritance(targetFile);
            }
            reloadPermissions();
            updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
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
                reloadPermissions();
                updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
            });
            window.open();
        } else {
            // Đã disable → enable lại
            if (permission.getUser() != null) {
                if (targetFolder != null) {
                    permissionService.enableInheritance(permission.getUser(), targetFolder);
                } else if (targetFile != null) {
                    permissionService.enableInheritance(permission.getUser(), targetFile);
                }
            } else if (permission.getRoleCode() != null) {
                ResourceRoleEntity role = permissionService.loadRoleByCode(permission.getRoleCode());
                if (targetFolder != null) {
                    permissionService.enableInheritance(role, targetFolder);
                } else if (targetFile != null) {
                    permissionService.enableInheritance(role, targetFile);
                }
            }

            reloadPermissions();
            updateInheritanceButtonLabel(permissionDataGrid.getSingleSelectedItem());
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
        } else {
            System.out.println("DEBUG: WARNING - Both reloadedUser and reloadedRole are NULL!");
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
        close(StandardOutcome.CLOSE);
    }

    private void reloadPermissions() {
        if (targetFolder != null) {
            loadPermissionsForFolder(targetFolder);
        } else if (targetFile != null) {
            loadPermissionsForFile(targetFile);
        }
    }
}