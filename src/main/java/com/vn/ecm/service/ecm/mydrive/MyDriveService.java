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

    public boolean assignDrive(User owner, Folder folder) {

        if (owner == null || folder == null) {
            notifications.create("Thiếu thông tin User hoặc Folder!")
                    .withDuration(2000)
                    .withType(Notifications.Type.WARNING)
                    .withCloseable(false)
                    .show();
            return false;
        }

        boolean folderAssigned = dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.myDrive = :folder", Long.class)
                .parameter("folder", folder)
                .one() > 0;

        if (folderAssigned) {
            notifications.create("Thư mục này đã được cấp cho user khác!")
                    .withDuration(2000)
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .show();
            return false;
        }

        boolean userHasDrive = dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.owner = :owner", Long.class)
                .parameter("owner", owner)
                .one() > 0;

        if (userHasDrive) {
            notifications.create("Người dùng này đã có My Drive!")
                    .withDuration(2000)
                    .withType(Notifications.Type.ERROR)
                    .withCloseable(false)
                    .show();
            return false;
        }

        Folder parent = folder.getParent();
        while (parent != null) {
            boolean parentAssigned = dataManager.loadValue(
                            "select count(c) from MyDriveConfig c where c.myDrive = :folder", Long.class)
                    .parameter("folder", parent)
                    .one() > 0;

            if (parentAssigned) {
                notifications.create("Thư mục cha đã được assign!")
                        .withDuration(2000)
                        .withType(Notifications.Type.ERROR)
                        .withCloseable(false)
                        .show();
                return false;
            }
            parent = parent.getParent();
        }

        MyDriveConfig config = dataManager.create(MyDriveConfig.class);
        config.setOwner(owner);
        config.setMyDrive(folder);
        dataManager.save(config);

        notifications.create("Cấp My Drive thành công!")
                .withDuration(2000)
                .withType(Notifications.Type.SUCCESS)
                .withCloseable(false)
                .show();
        return true;
    }

}
