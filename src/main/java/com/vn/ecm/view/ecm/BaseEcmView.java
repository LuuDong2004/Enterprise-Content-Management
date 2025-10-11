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

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base view dùng chung cho tất cả màn hình ECM (S3, WebDir...)
 * Các subclass chỉ cần override getCurrentStorage() để xác định kho đang dùng.
 */
@ViewController("ecm_BaseEcmView")
@ViewDescriptor("ECM-view.xml")
public abstract class BaseEcmView extends StandardView {

    private static final Logger log = LoggerFactory.getLogger(BaseEcmView.class);

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
            File fileIo = temporaryStorage.getFile(fileId);
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
        log.info("=== DOWNLOAD BUTTON CLICKED ===");
        
        FileDescriptor selectedFile = fileDataGird.getSingleSelectedItem();
        if (selectedFile == null) {
            log.warn("No file selected for download");
            notifications.create("Chưa chọn file để tải xuống.")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        log.info("Selected file: ID={}, Name={}, Size={}", 
                selectedFile.getId(), selectedFile.getName(), selectedFile.getSize());

        FileRef fileRef = selectedFile.getFileRef();
        if (fileRef == null) {
            log.error("FileRef is null for file: {}", selectedFile.getName());
            notifications.create("File này không có đường dẫn tải xuống hợp lệ.")
                    .withType(Notifications.Type.ERROR)
                    .show();
            return;
        }

        String storageName = fileRef.getStorageName();
        String filePath = fileRef.getPath();
        String fileName = fileRef.getFileName();
        
        log.info("FileRef details: StorageName={}, Path={}, FileName={}", 
                storageName, filePath, fileName);

        try {
            log.info("Ensuring storage is registered: {}", storageName);
            dynamicStorageManager.ensureRegistered(storageName);
            log.info("Storage registration completed successfully");

            log.info("Starting download process...");
            
            // Thử cách 1: Sử dụng Jmix downloader
            try {
                downloader.download(fileRef);
                log.info("Download initiated successfully via Jmix downloader");
            } catch (Exception e) {
                log.warn("Jmix downloader failed, trying direct approach: {}", e.getMessage());
                
                // Cách 2: Tự implement download
                SourceStorage sourceStorage = selectedFile.getSourceStorage();
                if (sourceStorage != null) {
                    FileStorage fileStorage = dynamicStorageManager.getOrCreate(sourceStorage);
                    log.info("Got FileStorage instance: {}", fileStorage.getClass().getSimpleName());
                    
                    // Tạo InputStream từ FileStorage
                    InputStream inputStream = fileStorage.openStream(fileRef);
                    log.info("Opened input stream successfully");
                    
                    // Đọc toàn bộ file thành byte array
                    byte[] fileBytes = inputStream.readAllBytes();
                    inputStream.close();
                    log.info("Read file bytes successfully, size: {} bytes", fileBytes.length);
                    
                    // Tạo download response
                    String downloadFileName = selectedFile.getName();
                    
                    // Sử dụng Jmix Downloader với byte array
                    downloader.download(fileBytes, downloadFileName);
                    log.info("Download initiated successfully via direct approach");
                } else {
                    throw new RuntimeException("SourceStorage is null");
                }
            }
            
            notifications.create("Đang tải xuống: " + selectedFile.getName())
                    .withType(Notifications.Type.DEFAULT)
                    .show();
        } catch (Exception e) {
            log.error("Error during download process", e);
            notifications.create("Lỗi khi tải xuống: " + e.getMessage())
                    .withType(Notifications.Type.ERROR)
                    .show();
        }
        
        log.info("=== DOWNLOAD PROCESS COMPLETED ===");
    }
}
