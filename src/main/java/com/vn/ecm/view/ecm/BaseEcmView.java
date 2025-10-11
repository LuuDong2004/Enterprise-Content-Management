package com.vn.ecm.view.ecm;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
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
import io.jmix.flowui.upload.TemporaryStorage;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base view dùng chung cho tất cả màn hình ECM (S3, WebDir...)
 * Các subclass chỉ cần override getCurrentStorage() để xác định kho đang dùng.
 */
@ViewController("ecm_BaseEcmView")
@ViewDescriptor("ECM-view.xml")
public abstract class BaseEcmView extends StandardView {

    // -----------------------------
    // UI Components
    // -----------------------------
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
    @ViewComponent
    private FileStorageUploadField fileRefField;

    // -----------------------------
    // Dependencies
    // -----------------------------
    @Autowired
    private TemporaryStorage temporaryStorage;
    @Autowired
    private Notifications notifications;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Downloader downloader;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;

    protected abstract SourceStorage getCurrentStorage();


    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        // folders trước
        foldersDl.setParameter("storage", getCurrentStorage());
        foldersDl.load();
        foldersTree.expandRecursively(foldersDc.getItems(), 1);

        // files: PHẢI set cả "folder" và "storage" trước khi load
        filesDl.setParameter("storage", getCurrentStorage());
        filesDl.setParameter("folder", null);   // quan trọng!
    }
    @Subscribe("foldersTree")
    public void onFoldersTreeItemClick(ItemClickEvent<Folder> e) {
        Folder selected = e.getItem();
        filesDl.setParameter("storage", getCurrentStorage());
        filesDl.setParameter("folder", selected);
        filesDl.load();
    }
    @Subscribe("fileRefField")
    public void onFileRefFieldFileUploadSucceeded(final FileUploadSucceededEvent<FileStorageUploadField> event) {
        Folder selected = foldersTree.getSingleSelectedItem();
        if (selected == null) {
            notifications.create("Hãy chọn thư mục trước khi upload.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }
        SourceStorage selectedStorage = getCurrentStorage();
        if (selectedStorage == null) {
            notifications.create("Không xác định được kho lưu trữ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        if (event.getReceiver() instanceof FileTemporaryStorageBuffer buffer) {
            UUID fileId = buffer.getFileData().getFileInfo().getId();
            java.io.File fileIo = temporaryStorage.getFile(fileId);
            if (fileIo != null) {
                try {
                    FileStorage dynamicFs = dynamicStorageManager.getOrCreate(selectedStorage);
                    String fileName = event.getFileName();
                    FileRef fileRef = temporaryStorage.putFileIntoStorage(fileId, fileName, dynamicFs);
                    fileRefField.setValue(fileRef);

                    // Lưu metadata file vào DB
                    Long length = event.getContentLength() > 0 ? event.getContentLength() : null;
                    FileDescriptor fileDescriptor = dataManager.create(FileDescriptor.class);
                    fileDescriptor.setId(UUID.randomUUID());
                    fileDescriptor.setName(event.getFileName());
                    fileDescriptor.setSize(length);
                    if (fileName.contains(".")) {
                        fileDescriptor.setExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
                    }
                    fileDescriptor.setLastModified(LocalDateTime.now());
                    fileDescriptor.setFolder(selected);
                    fileDescriptor.setFileRef(fileRef);
                    fileDescriptor.setSourceStorage(selectedStorage);
                    dataManager.save(fileDescriptor);

                    notifications.create("Tải lên thành công: " + fileName)
                            .withType(Notifications.Type.SUCCESS)
                            .show();

                    filesDl.load();
                } catch (Exception e) {
                    notifications.create("Lỗi khi lưu file: " + e.getMessage())
                            .withType(Notifications.Type.ERROR)
                            .show();
                }
            }
        }
    }
    @Subscribe(id = "btnDownload", subject = "clickListener")
    public void onBtnDownloadClick(final ClickEvent<JmixButton> event) {
        FileDescriptor selectedFile = fileDataGird.getSingleSelectedItem();
        if (selectedFile == null) {
            notifications.create("Chưa chọn file để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        if (selectedFile.getFileRef() == null) {
            notifications.create("File này không có đường dẫn tải xuống hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }
        SourceStorage storage = selectedFile.getSourceStorage();
        if (storage != null) {
            dynamicStorageManager.getOrCreate(storage);
            String storageName = selectedFile.getFileRef().getStorageName();
            if (storageName != null && storageName.startsWith("s3-")) {
                UUID sid = UUID.fromString(storageName.substring(3));
                storage = dataManager.load(SourceStorage.class).id(sid).optional().orElse(null);
                if (storage != null) dynamicStorageManager.getOrCreate(storage);
            }
        }
        downloader.download(selectedFile.getFileRef());
        notifications.create("Đang tải xuống: " + selectedFile.getName())
                .withType(Notifications.Type.DEFAULT)
                .show();
    }
}
