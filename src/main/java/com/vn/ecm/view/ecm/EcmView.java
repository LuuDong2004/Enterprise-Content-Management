package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.grid.ItemClickEvent;

import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.FileRejectedEvent;
import com.vaadin.flow.component.upload.SucceededEvent;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.minio.IMinioService;
import com.vn.ecm.view.main.MainView;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.receiver.MultiFileTemporaryStorageBuffer;
import io.jmix.flowui.component.upload.receiver.TemporaryStorageFileData;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
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
    private DataContext dataContext;
    @ViewComponent
    private CollectionLoader<File> filesDl;
    @Autowired
    private Notifications notifications;
    @Autowired
    private TemporaryStorage temporaryStorage;
    private Upload upload;

    @Autowired
    private IMinioService minioService;


    @Subscribe
    protected void onInit(InitEvent event) {

    }

    @Subscribe
    // load tree data folder
    public void treeDataGirdFolderShow(BeforeShowEvent event) {
        foldersDl.load();
        // mở từng cấp
        foldersTree.expandRecursively(foldersDc.getItems(), 1);
    }

    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(final ItemClickEvent<Folder> event) {
        Folder selected = event.getItem();
        filesDl.setParameter("folder", selected);
        filesDl.load();
    }

    @Subscribe("upload")
    public void onUploadSucceeded(final SucceededEvent event) {
        Folder selectedFolder = foldersTree.getSelectedItems().iterator().next();
        var upload = event.getSource();
        if (upload.getReceiver() instanceof MultiFileTemporaryStorageBuffer buffer) {
            for (var entry : buffer.getFiles().entrySet()) {
                TemporaryStorageFileData fileData = entry.getValue();
                String fileName = fileData.getFileName();
                java.io.File tempFile = fileData.getFileInfo().getFile();
                long size = tempFile.length();
                String extension = event.getFileName().contains(".")
                        ? event.getFileName().substring(event.getFileName().lastIndexOf('.') + 1)
                        : "";
                try (InputStream in = new FileInputStream(tempFile)) {
                    minioService.upload("ecm/upload", fileName, in, fileData.getMimeType());

                    File file = dataContext.create(File.class);
                    file.setId(UUID.randomUUID());
                    file.setName(fileName);
                    file.setSize(size);
                    file.setExtension(extension);
                    file.setLastModified(LocalDateTime.now());
                    file.setFolder(selectedFolder);
                    dataContext.save();
                } catch (Exception e) {
                    notifications.show("Lỗi upload!");
                }
                temporaryStorage.deleteFile(fileData.getFileInfo().getId());
            }
        }


    }


    @Subscribe("upload")
    public void onUploadFileRejected(final FileRejectedEvent event) {
        notifications.create("File bị từ chối: " + event.getErrorMessage()).withThemeVariant(NotificationVariant.LUMO_WARNING).withDuration(4000).show();
    }


}