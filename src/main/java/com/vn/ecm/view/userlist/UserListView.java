
package com.vn.ecm.view.userlist;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.*;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "user-list-view", layout = MainView.class)
@ViewController(id = "UserListView")
@ViewDescriptor(path = "user-list-view.xml")
public class UserListView extends StandardView {

    private User selectedUser;

    String path = "";
    @ViewComponent
    private TextField pathArea;

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

    @ViewComponent
    private JmixCheckbox usersFilterCb;
    @ViewComponent
    private JmixCheckbox rolesFilterCb;

    @Subscribe
    private void onInit(InitEvent event) {
        // Cột checkbox "Thêm"
        objectDataGrid.addColumn(
                new ComponentRenderer<>(dto -> {
                    Checkbox checkbox = new Checkbox(); // Vaadin Checkbox (OK)
                    checkbox.setValue(Boolean.TRUE.equals(dto.getSelected()));
                    checkbox.addValueChangeListener(e -> dto.setSelected(e.getValue()));
                    return checkbox;
                })
        ).setHeader("Thêm");

        usersFilterCb.addValueChangeListener(e -> {
            loadObjects();
        });

        rolesFilterCb.addValueChangeListener(e -> {
            loadObjects();
        });
        // (Tuỳ chọn) đặt mặc định: không tích ô nào → sẽ load cả Users lẫn Roles
        usersFilterCb.setValue(true);
        rolesFilterCb.setValue(true);
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

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (path != null) {
            pathArea.setValue("Đường dẫn: " + path);
        }
        loadObjects();
    }

    @Subscribe(id = "usersFilterCb", subject = "valueChangeEvent")
    private void onUsersFilterChanged(HasValue.ValueChangeEvent<Boolean> event) {
        loadObjects();
    }

    @Subscribe(id = "rolesFilterCb", subject = "valueChangeEvent")
    private void onRolesFilterChanged(HasValue.ValueChangeEvent<Boolean> event) {
        loadObjects();
    }

    private void loadObjects() {
        boolean usersChecked = Boolean.TRUE.equals(usersFilterCb.getValue());
        boolean rolesChecked = Boolean.TRUE.equals(rolesFilterCb.getValue());

        Map<String, Boolean> previouslySelected = objectsDc.getItems() == null ? Collections.emptyMap()
                : objectsDc.getItems().stream()
                .collect(Collectors.toMap(EcmObject::getId, x -> Boolean.TRUE.equals(x.getSelected()), (a, b) -> a));

        List<EcmObject> dtos = new ArrayList<>();
        if (usersChecked) {
            loadUsersInto(dtos);
        }
        if (rolesChecked) {
            loadRolesInto(dtos);
        }
        // Nếu không tích ô nào → không load gì cả
        if (!usersChecked && !rolesChecked) {
            dtos.clear();
        }
        // Phục hồi trạng thái đã chọn
        for (EcmObject dto : dtos) {
            if (previouslySelected.containsKey(dto.getId())) {
                dto.setSelected(previouslySelected.get(dto.getId()));
            }
        }
        objectsDc.setItems(dtos);
    }


    private void loadUsersInto(List<EcmObject> dtos) {
        usersDl.setParameter("file", selectedFile);
        usersDl.setParameter("folder", selectedFolder);
        usersDl.load();

        for (User u : usersDl.getContainer().getItems()) {
            EcmObject dto = new EcmObject();
            dto.setId(u.getId().toString());
            dto.setName(u.getUsername());
            dto.setType(ObjectType.USER);
            dtos.add(dto);
        }
    }

    private void loadRolesInto(List<EcmObject> dtos) {
        rolesDl.setParameter("file", selectedFile);
        rolesDl.setParameter("folder", selectedFolder);
        rolesDl.load();

        for (ResourceRoleEntity r : rolesDl.getContainer().getItems()) {
            EcmObject dto = new EcmObject();
            dto.setId(r.getCode());
            dto.setName(r.getName());
            dto.setType(ObjectType.ROLE);
            dtos.add(dto);
        }
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
