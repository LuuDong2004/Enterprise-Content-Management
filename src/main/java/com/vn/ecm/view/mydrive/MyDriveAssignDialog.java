package com.vn.ecm.view.mydrive;


import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.MyDriveConfig;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.mydrive.MyDriveService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
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


    @Autowired
    private Notifications notifications;

    private Folder folder;

    @ViewComponent
    private DataGrid<User> usersDataGrid;

    @Autowired
    private MyDriveService myDriveService;

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

        boolean ok = myDriveService.assignDrive(owner, folder);

        if (ok) {
            close(StandardOutcome.SAVE);
        }

    }

    @Subscribe(id = "cancelBtn", subject = "clickListener")
    public void onCancelBtnClick(final ClickEvent<JmixButton> event) {
        close(StandardOutcome.CLOSE);
    }
    
    
    
}