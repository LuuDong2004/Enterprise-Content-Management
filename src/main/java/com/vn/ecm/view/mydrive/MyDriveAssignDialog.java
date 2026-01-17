package com.vn.ecm.view.mydrive;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.MyDriveConfig;
import com.vn.ecm.entity.User;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Route(value = "my-drive-assign-dialog", layout = MainView.class)
@ViewController(id = "MyDriveAssignDialog")
@ViewDescriptor(path = "my-drive-assign-dialog.xml")
@DialogMode(width = "30%" , height = "40%")
public class MyDriveAssignDialog extends StandardView {
    @Autowired
    private DataManager dataManager;
    @ViewComponent
    private CollectionLoader<User> usersDl;
    @ViewComponent

    @Autowired
    private Notifications notifications;

    private Folder folder;

    @ViewComponent
    private DataGrid<User> usersDataGrid;

    @Subscribe
    public void onInit(final InitEvent event) {
        usersDl.load();
    }
    public void setFolder(UUID id) {
        if (id == null) {
            return;
        }
        folder = dataManager.load(Folder.class).id(id).one();

    }


    @Subscribe(id = "saveBtn", subject = "clickListener")
    public void onSaveBtnClick(final ClickEvent<JmixButton> event) {
        User owner = usersDataGrid.getSingleSelectedItem();

        if (owner == null || folder == null) {
            notifications.create("Vui lòng chọn người dùng để cấp Drive!")
                    .withDuration(2000)
                    .withType(Notifications.Type.WARNING)
                    .withCloseable(false)
                    .show();
            return;
        }
        // check folder đã bị gán cho user khác chưa
        boolean folderAssigned = dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.myDrive = :folder", Long.class)
                .parameter("folder", folder)
                .one() > 0;

        if (folderAssigned) {
            notifications.create("Thư mục này đã cấp quyền cho người khác !")
                    .withDuration(2000)
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .show();
            return;
        }

        // check nếu user đã có MyDrive rồi
        boolean exists = dataManager.load(MyDriveConfig.class)
                .query("select m from MyDriveConfig m where m.owner = :owner")
                .parameter("owner", owner)
                .optional()
                .isPresent();
        if (exists) {
            notifications.create("Người dùng này đã có My Drive")
                    .withDuration(2000)
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .show();
            return;
        }

        MyDriveConfig config = dataManager.create(MyDriveConfig.class);
        config.setOwner(owner);
        config.setMyDrive(folder);

        dataManager.save(config);
        close(StandardOutcome.SAVE);
        close(StandardOutcome.SAVE);
    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
    
    
    
}