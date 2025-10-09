package com.vn.ecm.view.editpermission;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.PermissionService;
import com.vn.ecm.view.main.MainView;
import com.vn.ecm.view.userlist.UserListView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Route(value = "edit-permission-view", layout = MainView.class)
@ViewController(id = "EditPermissionView")
@ViewDescriptor(path = "edit-permission-view.xml")
public class EditPermissionView extends StandardView {

    private ResourceRoleEntity selectedRole;
    private User selectedUser;
    String path = "";
    private EcmObject target;
    public void setTarget(EcmObject target) {
        this.target = target;
    }
    public void setPath(String path) {
        this.path = path;
    }

    private File selectedFile;
    private Folder selectedFolder;

    public void setTargetFile(File file) {
        this.selectedFile = file;
        this.selectedFolder = null;
    }

    public void setTargetFolder(Folder folder) {
        this.selectedFolder = folder;
        this.selectedFile = null;
    }

    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private DataGrid<EcmObject> objectDataGrid;
    @ViewComponent
    private DataGrid<Permission> permissionDataGrid;
    @ViewComponent
    private CollectionContainer<Permission> permissionsDc;
    @ViewComponent
    private TextArea pathArea;
    @ViewComponent
    private CollectionLoader<User> usersDl;
    @ViewComponent
    private CollectionLoader<ResourceRoleEntity> rolesDl;
    @ViewComponent
    private CollectionContainer<EcmObject> objectsDc;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private PermissionService permissionService;

    @Subscribe(id = "addBtn", subject = "clickListener")
    public void onAddBtnClick(final ClickEvent<JmixButton> event) {
        DialogWindow<UserListView> window = dialogWindows.view(this, UserListView.class).build();
        window.getView().setPath(path);
        // truyền target (folder hoặc file) sang UserListView
        if (selectedFile != null) {
            window.getView().setTargetFile(selectedFile);
        } else if (selectedFolder != null) {
            window.getView().setTargetFolder(selectedFolder);
        }
        window.addAfterCloseListener(afterCloseEvent -> {
            if (afterCloseEvent.closedWith(StandardOutcome.SAVE)) {
                // luôn set param, kể cả null
                usersDl.setParameter("file", selectedFile);
                usersDl.setParameter("folder", selectedFolder);
                usersDl.load();

                List<EcmObject> dtos = new ArrayList<>();
                for (User u : usersDl.getContainer().getItems()) {
                    EcmObject dto = new EcmObject(
                            u.getId().toString(),
                            ObjectType.USER,
                            u.getUsername()
                    );
                    dtos.add(dto);
                }
                // load role tương tự
                rolesDl.setParameter("file", selectedFile);
                rolesDl.setParameter("folder", selectedFolder);
                rolesDl.load();
                for (ResourceRoleEntity r : rolesDl.getContainer().getItems()) {
                    EcmObject dto = new EcmObject(
                            r.getCode(),
                            ObjectType.ROLE,
                            r.getName()
                    );
                    dtos.add(dto);
                }
                // cập nhật UI
                objectsDc.setItems(dtos);
            }
        });
        window.open();
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue(path);
        }
    }

    @Subscribe
    public void onInit(InitEvent event) {

        // Cột Allow
        permissionDataGrid.addColumn(
                new ComponentRenderer<>(permission -> {
                    Checkbox checkbox = new Checkbox();
                    checkbox.setValue(Boolean.TRUE.equals(permission.getAllow()));
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(true);
                            if (permission.getPermissionType().equals(PermissionType.MODIFY)) {
                                CollectionContainer<Permission> permissionsDc = getViewData().getContainer("permissionsDc");
                                for (Permission p : permissionsDc.getItems()) {
                                    if (p.getPermissionType().equals(PermissionType.READ)
                                            || p.getPermissionType().equals(PermissionType.CREATE)) {
                                        p.setAllow(true);
                                        permissionDataGrid.getDataProvider().refreshItem(p);
                                    }
                                }
                            }
                        } else {
                            permission.setAllow(false);
                        }
                        permissionDataGrid.getDataProvider().refreshItem(permission);
                    });
                    return checkbox;
                })
        ).setHeader("Allow");

        // Cột Deny
        permissionDataGrid.addColumn(
                new ComponentRenderer<>(permission -> {
                    Checkbox checkbox = new Checkbox();
                    checkbox.setValue(Boolean.FALSE.equals(permission.getAllow()));
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(false);
                            if (permission.getPermissionType().equals(PermissionType.READ)) {
                                CollectionContainer<Permission> permissionsDc = getViewData().getContainer("permissionsDc");
                                for (Permission p : permissionsDc.getItems()) {
                                    if (p.getPermissionType().equals(PermissionType.CREATE)
                                            || p.getPermissionType().equals(PermissionType.MODIFY)) {
                                        p.setAllow(false);
                                        permissionDataGrid.getDataProvider().refreshItem(p);
                                    }
                                }
                            }
                        } else {
                            permission.setAllow(true);
                        }
                        permissionDataGrid.getDataProvider().refreshItem(permission);
                    });
                    return checkbox;
                })
        ).setHeader("Deny");

        objectDataGrid.addSelectionListener(selection -> {
            Optional<EcmObject> optional = selection.getFirstSelectedItem();
            if (optional.isPresent()) {
                EcmObject dto = optional.get();

                CollectionContainer<Permission> permDc = getViewData().getContainer("permissionsDc");
                List<Permission> list = new ArrayList<>();

                if (dto.getType() == ObjectType.USER) {
                    // Load User entity
                    User user = dataManager.load(User.class)
                            .id(UUID.fromString(dto.getId()))
                            .one();
                    selectedUser = user;
                    selectedRole = null;

                    // Determine permission mask for the user & target
                    Permission dbPerm = null;
                    if (selectedFile != null) {
                        dbPerm = permissionService.loadPermission(user, selectedFile);
                    } else if (selectedFolder != null) {
                        dbPerm = permissionService.loadPermission(user, selectedFolder);
                    }
                    int mask = dbPerm != null && dbPerm.getPermissionMask() != null
                            ? dbPerm.getPermissionMask()
                            : 0;

                    // Build danh sách quyền cho User
                    for (PermissionType type : PermissionType.values()) {
                        Permission p = dataManager.create(Permission.class);
                        p.setUser(user);
                        if (selectedFile != null) p.setFile(selectedFile);
                        if (selectedFolder != null) p.setFolder(selectedFolder);
                        p.setPermissionType(type);
                        p.setAllow(PermissionType.hasPermission(mask, type));
                        list.add(p);
                    }

                    permDc.setItems(list);

                } else if (dto.getType() == ObjectType.ROLE) {
                    // Load Role entity by code or id (depending on how you stored dto.id)
                    ResourceRoleEntity role = dataManager.load(ResourceRoleEntity.class)
                            .query("select r from sec_ResourceRoleEntity r where r.code = :code")
                            .parameter("code", dto.getId())
                            .one();
                    selectedRole = role;
                    selectedUser = null;

                    Permission dbPerm = null;
                    if (selectedFile != null) {
                        dbPerm = permissionService.loadPermission(role, selectedFile);
                    } else if (selectedFolder != null) {
                        dbPerm = permissionService.loadPermission(role, selectedFolder);
                    }
                    int mask = dbPerm != null && dbPerm.getPermissionMask() != null
                            ? dbPerm.getPermissionMask()
                            : 0;
                    for (PermissionType type : PermissionType.values()) {
                        Permission p = dataManager.create(Permission.class);
                        p.setRoleCode(role.getCode());
                        if (selectedFile != null) p.setFile(selectedFile);
                        if (selectedFolder != null) p.setFolder(selectedFolder);
                        p.setPermissionType(type);
                        p.setAllow(PermissionType.hasPermission(mask, type));
                        list.add(p);
                    }
                    permDc.setItems(list);
                }
            }
        });
    }

    @Subscribe("usersBtn")
    public void onUsersBtnClick(ClickEvent<JmixButton> event) {
        usersDl.setParameter("file", selectedFile);
        usersDl.setParameter("folder", selectedFolder);
        usersDl.load();
        List<User> users = usersDl.getContainer().getItems();
        List<EcmObject> dtos = new ArrayList<>();
        for (User u : users) {
            EcmObject dto = new EcmObject();
            dto.setId(u.getId().toString());   // để sau này load User
            dto.setName(u.getUsername());
            dto.setType(ObjectType.USER);
            dtos.add(dto);
        }
        objectsDc.setItems(dtos);
    }

    @Subscribe("rolesBtn")
    public void onRolesBtnClick(ClickEvent<JmixButton> event) {
        rolesDl.setParameter("file", selectedFile);
        rolesDl.setParameter("folder", selectedFolder);
        rolesDl.load();
        List<ResourceRoleEntity> roles = rolesDl.getContainer().getItems();
        List<EcmObject> dtos = new ArrayList<>();
        for (ResourceRoleEntity r : roles) {
            EcmObject dto = new EcmObject();
            dto.setId(r.getCode());    // code dùng làm key cho role
            dto.setName(r.getName());
            dto.setType(ObjectType.ROLE);
            dtos.add(dto);
        }
        objectsDc.setItems(dtos);
    }

    @Subscribe(id = "saveBtn", subject = "clickListener")
    public void onSaveBtnClick(final ClickEvent<JmixButton> event) {
        CollectionContainer<Permission> permissionDc = getViewData().getContainer("permissionsDc");
        if (selectedUser != null) {
            if (selectedFolder != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedUser, selectedFolder);
            } else if (selectedFile != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedUser, selectedFile);
            }
        } else if (selectedRole != null) {
            if (selectedFolder != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedRole, selectedFolder);
            } else if (selectedFile != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedRole, selectedFile);
            }
        }
        Notification.show("Permission saved");
        close(StandardOutcome.SAVE);
    }
}