package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.grid.ItemClickEvent;


import com.vaadin.flow.component.upload.Upload;

import com.vaadin.flow.router.Route;

import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.minio.IMinioService;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.Id;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.component.upload.receiver.FileTemporaryStorageBuffer;

import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;

import org.springframework.beans.factory.annotation.Autowired;


import javax.management.Notification;
import java.time.LocalDateTime;
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
    private TemporaryStorage temporaryStorage;

    @ViewComponent
    private FileStorageUploadField fileRefField;

    @Autowired
    private Notifications notifications;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Downloader downloader;
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

    // upload file by using file storage
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        if (event.getReceiver() instanceof FileTemporaryStorageBuffer buffer) {
            UUID fileId = buffer.getFileData().getFileInfo().getId();
            java.io.File fileIo = temporaryStorage.getFile(fileId);
            if (fileIo != null) {
                FileRef fileRef = temporaryStorage.putFileIntoStorage(fileId, event.getFileName());
                fileRefField.setValue(fileRef);
                Folder selected = foldersTree.getSingleSelectedItem();
                if (selected == null) {
                    notifications.create("Hãy chọn thư mục trước khi upload.")
                            .withType(Notifications.Type.WARNING).show();
                    return;
                }
                try {
                    //add vào database
                    Long length = event.getContentLength() > 0 ? event.getContentLength() : null;
                    File fileDc = dataManager.create(File.class);
                    fileDc.setId(UUID.randomUUID());
                    fileDc.setName(event.getFileName());
                    fileDc.setSize(length);
                    fileDc.setExtension(event.getFileName().substring(event.getFileName().lastIndexOf(".") + 1));
                    fileDc.setLastModified(LocalDateTime.now());
                    fileDc.setFolder(selected);
                    fileDc.setFileRef(fileRef);
                    dataManager.save(fileDc);
                } catch (Exception e) {
                    notifications.create("Lỗi không lưu vào database được !").withType(Notifications.Type.ERROR).show();
                }
            }
        }
    }
   //download file
    @Subscribe(id = "btnDownload", subject = "clickListener")
    public void onBtnDownloadClick(final ClickEvent<JmixButton> event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.create("Hãy chọn thư mục trước khi tải xuống.")
                    .withType(Notifications.Type.WARNING).show();
            return;
        }
        File fileSelected = fileDataGird.getSingleSelectedItem();
        if (fileSelected == null) {
            notifications.create("Chưa chọn file trước khi tải xuống!.")
                    .withType(Notifications.Type.WARNING).show();
            return;
        }
        if (fileSelected.getFileRef() == null) {
            notifications.create("Đường dẫn tải xuống hỏng , không thể download file này.").show();
            return;
        }
        downloader.download(fileSelected.getFileRef());
    }


}