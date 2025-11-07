package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.Component;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.view.assignpermission.AssignPermissionView;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.action.list.ItemTrackingAction;
import io.jmix.flowui.component.UiComponentUtils;
import org.springframework.beans.factory.annotation.Autowired;

@ActionType("onObjectAssignPermission")
public class FilePermissionSelectedAction extends ItemTrackingAction<FileDescriptor> {

    @Autowired
    private Dialogs dialogs;

    String path = "";
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private Notifications notifications;

    public FilePermissionSelectedAction(String id) {
        super(id);
        setText("Phân quyền");
    }

    @Override
    public void actionPerform(Component component) {
        FileDescriptor file = getTarget().getSingleSelectedItem();
        if (file == null) {
            notifications.show("Vui lòng chọn file để phân quyền");
            return;
        }
        try {
            Folder folderParent = file.getFolder();
            String path = "";
            if (folderParent != null) {
                path = buildFolderPath(folderParent) + "/" + file.getName();
            } else {
                path = file.getName();
            }
            // Lấy View hiện tại (thường là parent view của action)
            var currentView = UiComponentUtils.getCurrentView();
            // Mở AssignPermissionView dưới dạng Dialog
            var window = dialogWindows.view(currentView, AssignPermissionView.class).build();
            window.getView().setTargetFile(file);
            window.getView().setPath(path);
            window.setResizable(true);
            window.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Đệ quy lấy path
    private String buildFolderPath(Folder folder) {
        if (folder == null) return "";
        StringBuilder path = new StringBuilder(folder.getName());
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }
}