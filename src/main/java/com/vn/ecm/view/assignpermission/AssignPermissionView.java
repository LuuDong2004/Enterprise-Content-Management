package com.vn.ecm.view.assignpermission;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.PermissionService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Route(value = "assign-permission-view", layout = MainView.class)
@ViewController(id = "AssignPermissionView")
@ViewDescriptor(path = "assign-permission-view.xml")
public class AssignPermissionView extends StandardView {

    @ViewComponent
    private TextArea pathArea;
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
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private PermissionService permissionService;

    List<User> username = new ArrayList<>();

    public void setUserName(List<User> username) {
        this.username = username;
    }

    String path = "";

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
        window.open();
    }

    @Subscribe(id = "usersBtn", subject = "clickListener")
    public void onUsersBtnClick(final ClickEvent<JmixButton> event) {
        // luôn set param, kể cả null
        usersDl.setParameter("file", selectedFile);
        usersDl.setParameter("folder", selectedFolder);
        usersDl.load();

        List<User> userList = usersDl.getContainer().getItems();
        List<EcmObject> dtos = userList.stream()
                .map(u -> new EcmObject(u.getId().toString(), ObjectType.USER, u.getUsername()))
                .toList();
        objectsDc.setItems(dtos);
    }

    @Subscribe(id = "rolesBtn", subject = "clickListener")
    public void onRolesBtnClick(final ClickEvent<JmixButton> event) {
        // tương tự
        rolesDl.setParameter("file", selectedFile);
        rolesDl.setParameter("folder", selectedFolder);
        rolesDl.load();

        List<ResourceRoleEntity> roles = rolesDl.getContainer().getItems();
        List<EcmObject> roleDtos = roles.stream()
                .map(r -> new EcmObject(
                        r.getCode(),
                        ObjectType.ROLE,
                        r.getName()))
                .toList();
        objectsDc.setItems(roleDtos);
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
                })).setHeader("Allow");

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
                })).setHeader("Deny");

        objectDataGrid.addSelectionListener(selection -> {
            Optional<EcmObject> optional = selection.getFirstSelectedItem();
            if (optional.isEmpty()) {
                permissionsDc.getMutableItems().clear();
                return;
            }
            EcmObject dto = optional.get();

            // Build Permission items list (one per PermissionType) and set into
            // permissionsDc
            List<Permission> list = new ArrayList<>();

            if (dto.getType() == ObjectType.USER) {
                // load user
                User user = dataManager.load(User.class)
                        .id(UUID.fromString(dto.getId()))
                        .one();

                // load DB permission for this principal on selected target (file or folder)
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
                    if (selectedFile != null)
                        p.setFile(selectedFile);
                    if (selectedFolder != null)
                        p.setFolder(selectedFolder);
                    p.setPermissionType(type);
                    p.setAllow(PermissionType.hasPermission(mask, type));
                    list.add(p);
                }
            } else if (dto.getType() == ObjectType.ROLE) {
                // load role
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
                    if (selectedFile != null)
                        p.setFile(selectedFile);
                    if (selectedFolder != null)
                        p.setFolder(selectedFolder);
                    p.setPermissionType(type);
                    p.setAllow(PermissionType.hasPermission(mask, type));
                    list.add(p);
                }
            }

            permissionsDc.setItems(list);
        });
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
}