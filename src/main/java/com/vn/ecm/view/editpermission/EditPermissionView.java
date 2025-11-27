package com.vn.ecm.view.editpermission;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
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

    @ViewComponent
    private CollectionLoader<Permission> permissionsDl;

    public void setPath(String path) {
        this.path = path;
    }

    public void setTarget(EcmObject target) {
        this.target = target;
    }

    private FileDescriptor selectedFile;
    private Folder selectedFolder;

    public void setTargetFile(FileDescriptor file) {
        this.selectedFile = file;
        this.selectedFolder = null;
    }

    public void setTargetFolder(Folder folder) {
        this.selectedFolder = folder;
        this.selectedFile = null;
    }

    @ViewComponent
    private Span principalTitle;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private DataGrid<EcmObject> objectDataGrid;
    @ViewComponent
    private DataGrid<Permission> permissionDataGrid;
    @ViewComponent
    private CollectionContainer<Permission> permissionsDc;
    @ViewComponent
    private TextField pathArea;
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
                usersDl.setParameter("file", selectedFile);
                usersDl.setParameter("folder", selectedFolder);
                usersDl.load();
                List<EcmObject> dtos = new ArrayList<>();
                for (User u : usersDl.getContainer().getItems()) {
                    EcmObject dto = new EcmObject();
                    dto.setId(u.getId().toString());
                    dto.setType(ObjectType.USER);
                    dto.setName(u.getUsername());
                    dtos.add(dto);
                }
                // load role tương tự
                rolesDl.setParameter("file", selectedFile);
                rolesDl.setParameter("folder", selectedFolder);
                rolesDl.load();
                for (ResourceRoleEntity r : rolesDl.getContainer().getItems()) {
                    EcmObject dto = new EcmObject();
                    dto.setId(r.getCode());
                    dto.setType(ObjectType.ROLE);
                    dto.setName(r.getName());
                    dtos.add(dto);
                }
                // cập nhật UI
                objectsDc.setItems(dtos);
            }
        });
        window.setWidth("60%");
        window.setHeight("70%");
        window.setResizable(true);
        window.open();
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue("Đường dẫn: " + path);
        }
        updatePermissionTitle(null);
        loadPrincipals();
    }

    @Subscribe
    public void onInit(InitEvent event) {
        permissionDataGrid.getColumnByKey("permissionType")
                .setRenderer(new TextRenderer<>(permission -> {
                    PermissionType type = permission.getPermissionType();
                    return type != null ? type.toString() : "";
                }));

        // Cột Allow
        permissionDataGrid.addColumn(
                new ComponentRenderer<>(permission -> {
                    Checkbox checkbox = new Checkbox();
                    checkbox.setValue(Boolean.TRUE.equals(permission.getAllow()));
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(true);
                            if (permission.getPermissionType().equals(PermissionType.MODIFY)) {
                                CollectionContainer<Permission> permissionsDc = getViewData()
                                        .getContainer("permissionsDc");
                                for (Permission p : permissionsDc.getItems()) {
                                    if (p.getPermissionType().equals(PermissionType.READ)
                                            || p.getPermissionType().equals(PermissionType.CREATE)) {
                                        p.setAllow(true);
                                        permissionDataGrid.getDataProvider().refreshItem(p);
                                    }
                                }
                            } else if (permission.getPermissionType().equals(PermissionType.CREATE)) {
                                CollectionContainer<Permission> permissionsDc = getViewData()
                                        .getContainer("permissionsDc");
                                for (Permission p : permissionsDc.getItems()) {
                                    if (p.getPermissionType().equals(PermissionType.READ)) {
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
                })).setHeader("Cho phép");

        // Cột Deny
        permissionDataGrid.addColumn(
                new ComponentRenderer<>(permission -> {
                    Checkbox checkbox = new Checkbox();
                    checkbox.setValue(Boolean.FALSE.equals(permission.getAllow()));
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(false);
                            if (permission.getPermissionType().equals(PermissionType.READ)) {
                                CollectionContainer<Permission> permissionsDc = getViewData()
                                        .getContainer("permissionsDc");
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
                })).setHeader("Từ chối");

        objectDataGrid.addSelectionListener(selection -> {
            Optional<EcmObject> optional = selection.getFirstSelectedItem();
            if (optional.isPresent()) {
                EcmObject dto = optional.get();
                updatePermissionTitle(dto);
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
                        if (selectedFile != null)
                            p.setFile(selectedFile);
                        if (selectedFolder != null)
                            p.setFolder(selectedFolder);
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
                        if (selectedFile != null)
                            p.setFile(selectedFile);
                        if (selectedFolder != null)
                            p.setFolder(selectedFolder);
                        p.setPermissionType(type);
                        p.setAllow(PermissionType.hasPermission(mask, type));
                        list.add(p);
                    }
                    permDc.setItems(list);
                }
            }
        });
        objectDataGrid.getColumnByKey("name")
                .setRenderer(new ComponentRenderer<>(obj -> {
                    Icon icon = (obj.getType() == ObjectType.USER)
                            ? VaadinIcon.USER.create()
                            : VaadinIcon.GROUP.create();

                    icon.setSize("var(--lumo-icon-size-s)");
                    if (obj.getType() == ObjectType.USER) {
                        icon.setColor("var(--lumo-primary-text-color)");
                    } else {
                        icon.setColor("var(--lumo-error-text-color)");
                    }

                    Span text = new Span(obj.getName() == null ? "" : obj.getName());
                    HorizontalLayout layout = new HorizontalLayout(icon, text);
                    layout.setPadding(false);
                    layout.setSpacing(true);
                    layout.setAlignItems(FlexComponent.Alignment.CENTER);
                    return layout;
                }));

        objectDataGrid.getColumnByKey("name")
                .setAutoWidth(true)
                .setSortable(true);
    }

    @Subscribe(id = "saveBtn", subject = "clickListener")
    public void onSaveBtnClick(final ClickEvent<JmixButton> event) {
        CollectionContainer<Permission> permissionDc = getViewData().getContainer("permissionsDc");
        if (selectedUser == null && selectedRole == null) {
            Notification.show("Please select a user or role");
            return;
        }
        if (selectedFolder == null && selectedFile == null) {
            Notification.show("Please select a folder or file");
            return;
        }
        boolean saved = false;
        if (selectedUser != null) {
            if (selectedFolder != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedUser, selectedFolder);
                saved = true;
            }
            if (selectedFile != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedUser, selectedFile);
                saved = true;
            }
        } else if (selectedRole != null) {
            if (selectedFolder != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedRole, selectedFolder);
                saved = true;
            }
            if (selectedFile != null) {
                permissionService.savePermission(permissionDc.getItems(), selectedRole, selectedFile);
                saved = true;
            }
        }
        if (saved) {
            permissionsDl.setParameter("file", selectedFile);
            permissionsDl.setParameter("folder", selectedFolder);
            permissionsDl.setParameter("user", selectedUser);
            permissionsDl.setParameter("roleCode", selectedRole != null ? selectedRole.getCode() : null);
            permissionsDl.load();
            Notification.show("Permission saved");
            close(StandardOutcome.SAVE);
        } else {
            Notification.show("No target selected for saving permissions");
        }
    }

    private void updatePermissionTitle(EcmObject dto) {
        if (principalTitle == null)
            return; // phòng lỗi NPE nếu XML chưa gắn id

        if (dto == null) {
            principalTitle.setText("Quyền truy cập cho ");
            return;
        }
        String name = dto.getName() != null ? dto.getName() : "";
        principalTitle.setText("Quyền truy cập cho " + name + ":");
    }

    private void loadPrincipals() {
        // luôn set param, kể cả null
        usersDl.setParameter("file", selectedFile);
        usersDl.setParameter("folder", selectedFolder);
        rolesDl.setParameter("file", selectedFile);
        rolesDl.setParameter("folder", selectedFolder);

        usersDl.load();
        rolesDl.load();

        List<User> userList = usersDl.getContainer().getItems();
        List<ResourceRoleEntity> roles = rolesDl.getContainer().getItems();

        List<EcmObject> principals = new ArrayList<>(userList.size() + roles.size());
        principals.addAll(userList.stream()
                .map(u -> new EcmObject(u.getId().toString(), ObjectType.USER, u.getUsername()))
                .toList());
        principals.addAll(roles.stream()
                .map(r -> new EcmObject(r.getCode(), ObjectType.ROLE, r.getName()))
                .toList());

        // Sắp xếp cho “gọn mắt”: theo tên rồi theo kiểu
        principals.sort((a, b) -> {
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            int cmp = an.compareToIgnoreCase(bn);
            return cmp != 0 ? cmp : a.getType().compareTo(b.getType());
        });

        objectsDc.setItems(principals);

        // Nếu không có principal nào thì xóa bảng quyền bên dưới
        if (principals.isEmpty()) {
            permissionsDc.getMutableItems().clear();
        }
    }

}