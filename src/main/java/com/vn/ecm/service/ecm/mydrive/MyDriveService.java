package com.vn.ecm.service.ecm.mydrive;

import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.MyDriveConfig;
import com.vn.ecm.entity.User;
import io.jmix.core.DataManager;
import io.jmix.flowui.Notifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyDriveService {
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Notifications notifications;

    public void assignDrive(User owner, Folder folder) {

        if (owner == null || folder == null) {
            throw new IllegalArgumentException("User hoặc Folder không hợp lệ");
        }

        // check folder đã bị assign chưa
        boolean folderAssigned = dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.myDrive = :folder", Long.class)
                .parameter("folder", folder)
                .one() > 0;

        if (folderAssigned) {
//            throw new IllegalStateException("Folder này đã được cấp cho user khác");
            notifications.create("Folder này đã được cấp cho user khác!")
                    .withDuration(2000)
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .show();
            return;
        }
        // check user đã có drive chưa
        boolean userHasDrive = dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.owner = :owner", Long.class)
                .parameter("owner", owner)
                .one() > 0;

        if (userHasDrive) {
           // throw new IllegalStateException("Người dùng này đã có My Drive");
            notifications.create("Người dùng này đã có My Drive!")
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
    }
}
