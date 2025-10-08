package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.grid.ItemClickEvent;

import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.SucceededEvent;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.receiver.MultiFileTemporaryStorageBuffer;
import io.jmix.flowui.component.upload.receiver.TemporaryStorageFileData;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

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
    public void onUploadFileRejected(final com.vaadin.flow.component.upload.FileRejectedEvent event) {
        notifications.create(event.getErrorMessage())
                .withThemeVariant(NotificationVariant.LUMO_WARNING)
                .withDuration(5000)
                .show();
    }

}