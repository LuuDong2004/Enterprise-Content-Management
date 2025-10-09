package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ItemClickEvent;


import com.vaadin.flow.router.Route;

import com.vn.ecm.ecm.storage.DynamicS3StorageManager;
import com.vn.ecm.ecm.storage.S3Storage;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.view.main.MainView;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
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


import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Route(value = "ECM-view", layout = MainView.class)
@ViewController(id = "EcmView")
@ViewDescriptor(path = "ECM-view.xml")
public class EcmView extends StandardView {
    @ViewComponent
    private CollectionContainer<Folder> foldersDc;
    @ViewComponent
    private CollectionContainer<FileDescriptor> filesDc;
    @ViewComponent
    private TreeDataGrid<Folder> foldersTree;
    @ViewComponent
    private CollectionLoader<Folder> foldersDl;
    @ViewComponent
    private DataGrid<FileDescriptor> fileDataGird;
    @ViewComponent
    private CollectionLoader<FileDescriptor> filesDl;

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
    @ViewComponent
    private ComboBox<?> storageComboBox;

    @Autowired
    private DynamicS3StorageManager dynamicS3StorageManager;

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
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.create("Hãy chọn thư mục trước khi upload.")
                    .withType(Notifications.Type.WARNING).show();
            return;
        }
        SourceStorage selectedStorage = resolveSelectedStorage();
        if (selectedStorage == null) {
            notifications.create("Chưa chọn kho lưu trữ!").show();
            return;
        }
        if (event.getReceiver() instanceof FileTemporaryStorageBuffer buffer) {
            UUID fileId = buffer.getFileData().getFileInfo().getId();
            java.io.File fileIo = temporaryStorage.getFile(fileId);
            if (fileIo != null) {
                FileStorage dynamicFs = dynamicS3StorageManager.getOrCreate(selectedStorage);
                String fileName = event.getFileName();   // tên hiển thị
                FileRef fileRef = temporaryStorage.putFileIntoStorage(fileId, fileName, dynamicFs);
                fileRefField.setValue(fileRef);

                try {
                    //add vào database
                    Long length = event.getContentLength() > 0 ? event.getContentLength() : null;
                    FileDescriptor fileDescriptorDc = dataManager.create(FileDescriptor.class);
                    fileDescriptorDc.setId(UUID.randomUUID());
                    fileDescriptorDc.setName(event.getFileName());
                    fileDescriptorDc.setSize(length);
                    fileDescriptorDc.setExtension(event.getFileName().substring(event.getFileName().lastIndexOf(".") + 1));
                    fileDescriptorDc.setLastModified(LocalDateTime.now());
                    fileDescriptorDc.setFolder(selected);
                    fileDescriptorDc.setFileRef(fileRef);
                    dataManager.save(fileDescriptorDc);
                } catch (Exception e) {
                    notifications.create("Lỗi không lưu vào database được !").withType(Notifications.Type.ERROR).show();
                }
            }
        }
    }
    private SourceStorage resolveSelectedStorage() {
        Object value = storageComboBox.getValue();
        if (value == null) return null;
        if (value instanceof SourceStorage) {
            return (SourceStorage) value;
        }
        if (value instanceof StorageType st) {
            return dataManager.load(SourceStorage.class)
                    .query("select s from SourceStorage s where s.active = true")
                    .list()
                    .stream()
                    .filter(s -> s.getType() == st)
                    .findFirst()
                    .orElse(null);
        }
        if (value instanceof String str) { // e.g. enum id from UI binding
            StorageType st = StorageType.fromId(str);
            if (st == null) return null;
            return dataManager.load(SourceStorage.class)
                    .query("select s from SourceStorage s where s.active = true")
                    .list()
                    .stream()
                    .filter(s -> s.getType() == st)
                    .findFirst()
                    .orElse(null);
        }
        return null;
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
        FileDescriptor fileDescriptorSelected = fileDataGird.getSingleSelectedItem();
        if (fileDescriptorSelected == null) {
            notifications.create("Chưa chọn file trước khi tải xuống!.")
                    .withType(Notifications.Type.WARNING).show();
            return;
        }
        if (fileDescriptorSelected.getFileRef() == null) {
            notifications.create("Đường dẫn tải xuống hỏng , không thể download file này.").show();
            return;
        }
        downloader.download(fileDescriptorSelected.getFileRef());
    }
}