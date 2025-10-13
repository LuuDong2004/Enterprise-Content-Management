package com.vn.ecm.view.userlist;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Route(value = "user-list-view", layout = MainView.class)
@ViewController(id = "UserListView")
@ViewDescriptor(path = "user-list-view.xml")
public class UserListView extends StandardView {

    private User selectedUser;

    String path = "";
    @ViewComponent
    private JmixTextArea pathArea;

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

    @ViewComponent
    private CollectionLoader<User> usersDl;
    @ViewComponent
    private CollectionLoader<ResourceRoleEntity> rolesDl;
    @ViewComponent
    private CollectionContainer<EcmObject> objectsDc;
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private DataGrid<EcmObject> objectDataGrid;
    @ViewComponent
    private CollectionLoader<Permission> permissionsDl;

    @Subscribe
    private void onInit(InitEvent event) {
        objectDataGrid.addColumn(
                new ComponentRenderer<>(permission -> {
                    Checkbox checkbox = new Checkbox();
                    checkbox.setValue(Boolean.TRUE.equals(permission.getSelected()));
                    checkbox.addValueChangeListener(e -> {
                        permission.setSelected(e.getValue());
                    });
                    return checkbox;
                })
        ).setHeader("Add");
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue(path);
        }
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

    @Subscribe("applyBtn")
    public void onApplyBtnClick(ClickEvent<JmixButton> event) {
        // Lấy các object được chọn trong bảng
        List<EcmObject> selectedObjects = new ArrayList<>();
        for (EcmObject dto : objectsDc.getItems()) {
            if (Boolean.TRUE.equals(dto.getSelected())) {
                selectedObjects.add(dto);
            }
        }

        List<Permission> toSave = new ArrayList<>();
        User selectedUser = null;
        String selectedRoleCode = null;

        for (EcmObject dto : selectedObjects) {
            Permission p = dataManager.create(Permission.class);

            // Gán file hoặc folder tương ứng
            if (selectedFile != null) {
                p.setFile(selectedFile);
                p.setFolder(null);
            } else if (selectedFolder != null) {
                p.setFolder(selectedFolder);
                p.setFile(null);
            }

            // Xác định là USER hay ROLE
            if (dto.getType() == ObjectType.USER) {
                User user = dataManager.load(User.class).id(UUID.fromString(dto.getId())).one();
                p.setUser(user);
                selectedUser = user;
            } else if (dto.getType() == ObjectType.ROLE) {
                p.setRoleCode(dto.getId());
                selectedRoleCode = dto.getId();
            }

            // Gán loại permission mặc định (nếu có)
            // p.setPermissionType(PermissionType.ALLOW);
            p.setInheritEnabled(true);
            p.setInherited(false);

            toSave.add(p);
        }

        // Lưu tất cả permission
        dataManager.save(toSave.toArray());

        // Reload lại danh sách permission
        permissionsDl.setParameter("file", selectedFile);
        permissionsDl.setParameter("folder", selectedFolder);
        permissionsDl.setParameter("user", selectedUser);
        permissionsDl.setParameter("roleCode", selectedRoleCode);
        permissionsDl.load();

        close(StandardOutcome.SAVE);
    }

}