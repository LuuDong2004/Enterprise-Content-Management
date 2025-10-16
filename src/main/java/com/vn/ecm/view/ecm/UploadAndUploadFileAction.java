package com.vn.ecm.view.ecm;


import com.vn.ecm.service.ecm.FileDescriptorService;
import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.action.list.ItemTrackingAction;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.component.upload.receiver.FileTemporaryStorageBuffer;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.upload.TemporaryStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;


import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;
import java.util.function.Supplier;


@ActionType("UploadDownloadFile")
@Component("ecm_UploadAndUploadFileAction")
@Scope("prototype")
public class UploadAndUploadFileAction extends ItemTrackingAction<FileDescriptor> {

    public enum Mode {UPLOAD, DOWNLOAD}
    @Autowired
    private TemporaryStorage tempStorage;

    @Autowired
    private Downloader downloader;

    private Mode mode = Mode.UPLOAD;

    @Autowired
    private FileDescriptorService fileDescriptorService;

    private Supplier<Folder> folderSupplier;
    private Supplier<SourceStorage> storageSupplier;

    private FileUploadSucceededEvent<FileStorageUploadField> uploadEvent;

    public void setUploadEvent(FileUploadSucceededEvent<FileStorageUploadField> uploadEvent) {
        this.uploadEvent = uploadEvent;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
    public void setFolderSupplier(Supplier<Folder> folderSupplier) {
        this.folderSupplier = folderSupplier;
    }
    public void setStorageSupplier(Supplier<SourceStorage> storageSupplier) {
        this.storageSupplier = storageSupplier;
    }
    public UploadAndUploadFileAction(String id) {
        super(id);
    }
    public UploadAndUploadFileAction() {
        this("UploadDownloadFile");
    }
    @Override
    public void execute() {
        if (mode == Mode.UPLOAD) {
            upload();
        } else {
            download();
        }
    }
    // Enable/disable tự động cho DOWNLOAD khi không chọn dòng
    @Override
    protected boolean isApplicable() {
        return mode == Mode.UPLOAD
                || (getTarget() != null && getTarget().getSingleSelectedItem() != null);
    }
    private void upload() {
        Folder folder = folderSupplier != null ? folderSupplier.get() : null;
        if (folder == null) return;

        SourceStorage storage = storageSupplier != null ? storageSupplier.get() : null;
        if (storage == null) return;

        if (uploadEvent == null) return;

        Object receiver = uploadEvent.getReceiver();
        if (!(receiver instanceof FileTemporaryStorageBuffer)) return;
        FileTemporaryStorageBuffer buf = (FileTemporaryStorageBuffer) receiver;

        try {
            UUID fileId = buf.getFileData().getFileInfo().getId();
            File tmp = tempStorage.getFile(fileId);
            if (tmp == null) return;

            fileDescriptorService.uploadFile(
                    fileId,
                    uploadEvent.getFileName(),
                    uploadEvent.getContentLength() > 0 ? uploadEvent.getContentLength() : null,
                    folder,
                    storage
            );
        } catch (Exception e) {
            throw new RuntimeException("Upload failed for: " + uploadEvent.getFileName(), e);
        }
    }

    private void download() {
        FileDescriptor selected = getTarget() != null ? getTarget().getSingleSelectedItem() : null;
        if (selected == null) {
            return;
        }
        if (selected.getFileRef() == null) {
            return;
        }
        if (selected.getSourceStorage() == null) {
            return;
        }
        try {
            byte[] bytes = fileDescriptorService.downloadFile(selected);
            downloader.download(bytes, selected.getName());
        } catch (Exception e) {
            throw new RuntimeException("Download failed for: " + selected.getName(), e);
        }
    }

}

