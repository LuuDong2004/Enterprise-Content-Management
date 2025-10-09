package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.grid.ItemClickEvent;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.FileRejectedEvent;
import com.vaadin.flow.component.upload.SucceededEvent;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.view.assignpermission.AssignPermissionView;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.receiver.MultiFileTemporaryStorageBuffer;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "ECM-view", layout = MainView.class)
@ViewController(id = "EcmView")
@ViewDescriptor(path = "ECM-view.xml")
public class EcmView extends StandardView {

    @ViewComponent
    private CollectionContainer<Folder> foldersDc;
    @ViewComponent
    private CollectionContainer<File> filesDc;
    @ViewComponent
    private TreeDataGrid<Folder> foldersTree;
    @ViewComponent
    private CollectionLoader<Folder> foldersDl;
    @ViewComponent
    private DataGrid<File> fileDataGird;
    @ViewComponent
    private CollectionLoader<File> filesDl;
    @Autowired
    private Notifications notifications;
    @Autowired
    private TemporaryStorage temporaryStorage;
    private Upload upload;
    @Autowired
    private DialogWindows dialogWindows;

    @Subscribe
    protected void onInit(InitEvent event) {

    }
    @Subscribe
    // load tree data folder
    public void treeDataGirdFolderShow(BeforeShowEvent event) {
       foldersDl.load();
       // mở từng cấp
       foldersTree.expandRecursively(foldersDc.getItems(),1);
    }

    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(final ItemClickEvent<Folder> event) {
        Folder selected = event.getItem();
        filesDl.setParameter("folderId", selected != null ? selected.getId() : null);
        filesDl.load();
    }

    @Subscribe("upload")
    public void onUploadSucceeded(final SucceededEvent event) {
        StringBuilder sb = new StringBuilder("<div>Uploaded file(s):");
        if (event.getUpload().getReceiver() instanceof MultiFileTemporaryStorageBuffer buffer) {
        }
    }

    @Subscribe("upload")
    public void onUploadFileRejected(final FileRejectedEvent event) {
        notifications.create(event.getErrorMessage())
                .withThemeVariant(NotificationVariant.LUMO_WARNING)
                .withDuration(5000)
                .show();
    }

    @Subscribe("foldersTree.assignPermission")
    public void onFoldersTreeAssignPermission(final ActionPerformedEvent event) {
        Folder folder = foldersTree.getSingleSelectedItem();
        if (folder == null) {
            Notification.show("Null");
            return;
        }
        try {
            String path = buildFolderPath(folder);
            DialogWindow<AssignPermissionView> window = dialogWindows.view(this, AssignPermissionView.class).build();
            window.getView().setTargetFolder(folder);
            window.getView().setPath(path);
            window.open();
        } catch (Exception e) {
            e.printStackTrace();
            Notification.show("Lỗi khi mở popup: " + e.getMessage());
        }
    }

    // đệ quy lấy path
    private String buildFolderPath(Folder folder) {
        StringBuilder path = new StringBuilder(folder.getName());
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }

}