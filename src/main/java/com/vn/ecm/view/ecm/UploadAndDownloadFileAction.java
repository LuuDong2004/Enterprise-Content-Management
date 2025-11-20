package com.vn.ecm.view.ecm;

import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.entity.*;
import com.vn.ecm.ocr.log.OcrFileTextSearchService;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import com.vn.ecm.service.ecm.PermissionService;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.action.list.ItemTrackingAction;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.component.upload.receiver.FileTemporaryStorageBuffer;
import io.jmix.flowui.download.DownloadFormat;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.upload.TemporaryStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;
import java.util.function.Supplier;

@ActionType("UploadDownloadFile")
@Component("ecm_UploadAndUploadFileAction")
@Scope("prototype")
public class UploadAndDownloadFileAction extends ItemTrackingAction<FileDescriptor> {

    public enum Mode {
        UPLOAD, DOWNLOAD
    }

    @Autowired
    private TemporaryStorage tempStorage;

    @Autowired
    private Downloader downloader;

    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private Notifications notifications;

    private Mode mode = Mode.UPLOAD;
    @Autowired
    private S3ClientFactory s3ClientFactory;

    @Autowired
    private IFileDescriptorUploadAndDownloadService fileDescriptorService;
    @Autowired
    private OcrFileTextSearchService ocrFileTextSearchService;

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

    public UploadAndDownloadFileAction(String id) {
        super(id);
    }

    public UploadAndDownloadFileAction() {
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
        if (folder == null)
            return;
        User user = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(user, PermissionType.CREATE, folder);
        if (!per) {
            notifications.create("Bạn không có quyền tải file này lên hệ thống.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .show();
            return;
        }
        SourceStorage storage = storageSupplier != null ? storageSupplier.get() : null;
        if (storage == null)
            return;

        if (uploadEvent == null)
            return;
        Object receiver = uploadEvent.getReceiver();
        if (!(receiver instanceof FileTemporaryStorageBuffer))
            return;
        FileTemporaryStorageBuffer buf = (FileTemporaryStorageBuffer) receiver;
        UUID fileId = buf.getFileData().getFileInfo().getId();
        File tmp = tempStorage.getFile(fileId);
        if (tmp == null)
            return;
        FileDescriptor fileDescriptor = fileDescriptorService.uploadFile(
                fileId,
                uploadEvent.getFileName(),
                uploadEvent.getContentLength() > 0 ? uploadEvent.getContentLength() : null,
                folder,
                storage,
                user.getUsername(),
                tmp);
        if (fileDescriptor != null) {
            permissionService.initializeFilePermission(user, fileDescriptor);
            ocrFileTextSearchService.getExtractedText(fileDescriptor.getId())
                    .filter(text -> !text.isBlank())
                    .ifPresent(text -> {
                        String preview = text.length() > 250 ? text.substring(0, 250) + "..." : text;
                        notifications.create("OCR đã lưu")
                                .withType(Notifications.Type.SUCCESS)
                                .withDuration(2000)
                                .withCloseable(false)
                                .show();
                    });
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
        String result = s3ClientFactory.testConnection(selected.getSourceStorage());
        User userCurr = (User) currentAuthentication.getUser();
        boolean per = permissionService.hasPermission(userCurr, PermissionType.READ, selected);
        if (!per) {
            notifications.create("Bạn không có quyền tải xuống File này.")
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
            return;
        }
        try {
            byte[] bytes = fileDescriptorService.downloadFile(selected);
            downloader.download(bytes, selected.getName(), DownloadFormat.OCTET_STREAM);
        } catch (Exception e) {
            // throw new RuntimeException("Download failed for: " + selected.getName(), e);
            notifications.create(result)
                    .withType(Notifications.Type.ERROR)
                    .withDuration(2000)
                    .withCloseable(false)
                    .show();
        }
    }
}
