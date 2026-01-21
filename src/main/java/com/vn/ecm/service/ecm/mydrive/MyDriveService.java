package com.vn.ecm.service.ecm.mydrive;

import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import io.jmix.core.DataManager;
import io.jmix.flowui.Notifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyDriveService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Notifications notifications;

    @Autowired
    private PermissionService permissionService;

    public boolean assignDrive(User owner, Folder folder) {

        if (owner == null || folder == null) {
            notifyWarn("Thiếu thông tin User hoặc Folder!");
            return false;
        }

        // user đã có MyDrive
        if (userHasDrive(owner)) {
            notifyError("Người dùng này đã có My Drive!");
            return false;
        }

        // folder đã bị assign
        if (isFolderAssigned(folder)) {
            notifyError("Thư mục này đã được cấp cho user khác!");
            return false;
        }

        // kiểm tra CHA
        if (isAnyParentAssigned(folder)) {
            notifyError("Thư mục cha đã được cấp My Drive!");
            return false;
        }

        // kiểm tra CON (toàn bộ subtree)
        if (isAnyDescendantAssigned(folder)) {
            notifyError("Có thư mục con đã được cấp My Drive!");
            return false;
        }

        MyDriveConfig config = dataManager.create(MyDriveConfig.class);
        config.setOwner(owner);
        config.setMyDrive(folder);
        dataManager.save(config);

        notifySuccess("Cấp My Drive thành công!");
        return true;
    }


    public Folder getMyDriveByUser(User user) {
        if (user == null) return null;

        return dataManager.load(MyDriveConfig.class)
                .query("select c from MyDriveConfig c where c.owner = :user")
                .parameter("user", user)
                .optional()
                .map(MyDriveConfig::getMyDrive)
                .orElse(null);
    }


    public List<Folder> getSubFolders(User user, Folder parent) {

        Folder myDrive = getMyDriveByUser(user);
        if (!isInsideMyDrive(parent, myDrive)) {
            return List.of();
        }

        return dataManager.load(Folder.class)
                .query("""
                        select f from Folder f
                        where f.parent = :parent
                          and f.inTrash = false
                        """)
                .parameter("parent", parent)
                .list()
                .stream()
                .filter(f -> permissionService.hasPermission(user, PermissionType.READ, f))
                .toList();
    }


    public List<FileDescriptor> getFiles(User user, Folder folder) {

        Folder myDrive = getMyDriveByUser(user);
        if (!isInsideMyDrive(folder, myDrive)) {
            return List.of();
        }

        return dataManager.load(FileDescriptor.class)
                .query("""
                        select f from FileDescriptor f
                        where f.folder = :folder
                          and f.inTrash = false
                        """)
                .parameter("folder", folder)
                .list()
                .stream()
                .filter(f -> permissionService.hasPermission(user, PermissionType.READ, f))
                .toList();
    }


    private boolean userHasDrive(User user) {
        return dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.owner = :owner",
                        Long.class)
                .parameter("owner", user)
                .one() > 0;
    }

    private boolean isFolderAssigned(Folder folder) {
        return dataManager.loadValue(
                        "select count(c) from MyDriveConfig c where c.myDrive = :folder",
                        Long.class)
                .parameter("folder", folder)
                .one() > 0;
    }

    /** kiểm tra CHA */
    private boolean isAnyParentAssigned(Folder folder) {
        Folder current = folder.getParent();
        while (current != null) {
            if (isFolderAssigned(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * kiểm tra CON (đệ quy toàn bộ cây)
     * FIX QUAN TRỌNG
     */
    private boolean isAnyDescendantAssigned(Folder folder) {

        List<Folder> children = dataManager.load(Folder.class)
                .query("select f from Folder f where f.parent = :parent")
                .parameter("parent", folder)
                .list();

        for (Folder child : children) {
            if (isFolderAssigned(child)) {
                return true;
            }
            if (isAnyDescendantAssigned(child)) {
                return true;
            }
        }
        return false;
    }

    /** boundary MyDrive */
    private boolean isInsideMyDrive(Folder folder, Folder myDrive) {
        if (folder == null || myDrive == null) return false;

        Folder current = folder;
        while (current != null) {
            if (current.equals(myDrive)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /* =====================================================
     * NOTIFICATIONS
     * ===================================================== */
    private void notifyWarn(String msg) {
        notifications.create(msg)
                .withDuration(2000)
                .withType(Notifications.Type.WARNING)
                .withCloseable(false)
                .show();
    }

    private void notifyError(String msg) {
        notifications.create(msg)
                .withDuration(2000)
                .withType(Notifications.Type.ERROR)
                .withCloseable(false)
                .show();
    }

    private void notifySuccess(String msg) {
        notifications.create(msg)
                .withDuration(2000)
                .withType(Notifications.Type.SUCCESS)
                .withCloseable(false)
                .show();
    }
}
