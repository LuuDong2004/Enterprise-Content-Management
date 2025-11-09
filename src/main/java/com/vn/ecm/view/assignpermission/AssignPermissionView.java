
package com.vn.ecm.view.assignpermission;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.view.advancedpermission.AdvancedPermissionView;
import com.vn.ecm.view.editpermission.EditPermissionView;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.component.html.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Route(value = "assign-permission-view", layout = MainView.class)
@ViewController(id = "AssignPermissionView")
@ViewDescriptor(path = "assign-permission-view.xml")
public class AssignPermissionView extends StandardView {

    @ViewComponent
    private TextField pathArea;
    @ViewComponent
    private DataGrid<EcmObject> objectDataGrid;
    @ViewComponent
    private DataGrid<Permission> permissionDataGrid;
    @ViewComponent
    private CollectionContainer<Permission> permissionsDc;
    @ViewComponent
    private CollectionLoader<User> usersDl;
    @ViewComponent
    private CollectionLoader<ResourceRoleEntity> rolesDl;
    @ViewComponent
    private CollectionContainer<EcmObject> objectsDc;
    @ViewComponent
    private Span principalTitle;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private PermissionService permissionService;
    @ViewComponent
    private Icon principalIcon;

    List<User> username = new ArrayList<>();

    public void setUserName(List<User> username) {
        this.username = username;
    }

    String path = "";

    public void setPath(String path) {
        this.path = path;
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

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue("Đường dẫn: " + path);
        }
        updatePermissionTitle(null);
        loadPrincipals(); //nạp danh sách chung người dùng + phòng ban
    }

    // NEW: nạp Users + Roles rồi gộp thành một list EcmObject
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

    @Subscribe
    public void onInit(InitEvent event) {
        // Hiển thị tên quyền
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
                    checkbox.setReadOnly(true);
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(true);
                        } else if (Boolean.TRUE.equals(permission.getAllow())) {
                            permission.setAllow(null);
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
                    checkbox.setReadOnly(true);
                    checkbox.addValueChangeListener(e -> {
                        if (e.getValue()) {
                            permission.setAllow(false);
                        }
                    });
                    return checkbox;
                })).setHeader("Từ chối");

        // Khi chọn 1 principal (user/role) -> tính ra danh sách quyền tương ứng
        objectDataGrid.addSelectionListener(selection -> {
            Optional<EcmObject> optional = selection.getFirstSelectedItem();
            if (optional.isEmpty()) {
                permissionsDc.getMutableItems().clear();
                updatePermissionTitle(null); // NEW
                return;
            }
            EcmObject dto = optional.get();
            updatePermissionTitle(dto);

            List<Permission> list = new ArrayList<>();
            if (dto.getType() == ObjectType.USER) {
                User user = dataManager.load(User.class)
                        .id(UUID.fromString(dto.getId()))
                        .one();

                Permission dbPerm = null;
                if (selectedFile != null) {
                    dbPerm = permissionService.loadPermission(user, selectedFile);
                } else if (selectedFolder != null) {
                    dbPerm = permissionService.loadPermission(user, selectedFolder);
                }
                int mask = dbPerm != null && dbPerm.getPermissionMask() != null ? dbPerm.getPermissionMask() : 0;
                for (PermissionType type : PermissionType.values()) {
                    Permission p = dataManager.create(Permission.class);
                    p.setUser(user);
                    if (selectedFile != null) p.setFile(selectedFile);
                    if (selectedFolder != null) p.setFolder(selectedFolder);
                    p.setPermissionType(type);
                    p.setAllow(PermissionType.hasPermission(mask, type));
                    list.add(p);
                }
            } else if (dto.getType() == ObjectType.ROLE) {
                ResourceRoleEntity role = dataManager.load(ResourceRoleEntity.class)
                        .query("select r from sec_ResourceRoleEntity r where r.code = :code")
                        .parameter("code", dto.getId())
                        .one();

                Permission dbPerm = null;
                if (selectedFile != null) {
                    dbPerm = permissionService.loadPermission(role, selectedFile);
                } else if (selectedFolder != null) {
                    dbPerm = permissionService.loadPermission(role, selectedFolder);
                }
                int mask = dbPerm != null && dbPerm.getPermissionMask() != null ? dbPerm.getPermissionMask() : 0;
                for (PermissionType type : PermissionType.values()) {
                    Permission p = dataManager.create(Permission.class);
                    p.setRoleCode(role.getCode());
                    if (selectedFile != null) p.setFile(selectedFile);
                    if (selectedFolder != null) p.setFolder(selectedFolder);
                    p.setPermissionType(type);
                    p.setAllow(PermissionType.hasPermission(mask, type));
                    list.add(p);
                }
            }
            permissionsDc.setItems(list);
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

    @Subscribe(id = "editBtn", subject = "clickListener")
    public void onEditBtnClick(final ClickEvent<JmixButton> event) {
        EcmObject seleted = objectDataGrid.getSingleSelectedItem();
        DialogWindow<EditPermissionView> window = dialogWindows.view(this, EditPermissionView.class).build();
        if (this.selectedFile != null) {
            window.getView().setTargetFile(this.selectedFile);
        } else if (this.selectedFolder != null) {
            window.getView().setTargetFolder(this.selectedFolder);
        }
        window.getView().setPath(path);
        window.getView().setTarget(seleted);
        window.setWidth("60%");
        window.setHeight("70%");
        window.setResizable(true);
        window.open();
    }

    @Subscribe(id = "advanceBtn", subject = "clickListener")
    public void onAdvanceBtnClick(final ClickEvent<JmixButton> event) {
        EcmObject seleted = objectDataGrid.getSingleSelectedItem();
        DialogWindow<AdvancedPermissionView> window = dialogWindows.view(this, AdvancedPermissionView.class).build();
        if (this.selectedFile != null) {
            window.getView().setTargetFile(this.selectedFile);
        } else if (this.selectedFolder != null) {
            window.getView().setTargetFolder(this.selectedFolder);
        }
        window.getView().setPath(path);
        window.getView().setTarget(seleted);
        window.open();
    }

    private void updatePermissionTitle(EcmObject dto) {
        if (principalTitle == null) return; // phòng lỗi NPE nếu XML chưa gắn id

        if (dto == null) {
            principalTitle.setText("Quyền truy cập cho ");
            return;
        }
        String typeLabel = (dto.getType() == ObjectType.USER) ? "Người dùng" : "Phòng ban";
        String name = dto.getName() != null ? dto.getName() : "";
        principalTitle.setText("Quyền truy cập cho " + name + ":");
    }

}
