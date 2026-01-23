package com.vn.ecm.service.ecm.folderandfile.Impl;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.*;
import com.vn.ecm.ocr.log.OcrFileTextSearchService;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.upload.TemporaryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileDescriptorUploadAndDownloadService implements IFileDescriptorUploadAndDownloadService {
    private static final Logger log = LoggerFactory.getLogger(FileDescriptorUploadAndDownloadService.class);

    @Autowired
    private DataManager dataManager;
    @Autowired
    private TemporaryStorage tempStorage;
    @Autowired
    private DynamicStorageManager storageManager;
    @Autowired
    private FileDescriptorService fileDescriptorService;
    @Autowired
    private OcrFileTextSearchService ocrFileTextSearchService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    public FileDescriptor uploadFile(UUID fileId,
            String fileName,
            Long contentLength,
            Folder folder,
            SourceStorage sourceStorage,
            String username,
            File tempFile) {

        File ocrTempCopy = duplicateTempFile(tempFile, fileName);
        try {
            User user = (User) currentAuthentication.getUser();
            boolean permission = permissionService.hasPermission(user, PermissionType.CREATE,folder);
            if(!permission){
                throw new AccessDeniedException(
                        "Tệp",
                        "Xem",
                        "Tải lên"
                );
            }
            String uniqueName = fileDescriptorService.suggestUniqueName(folder, sourceStorage, fileName);
            FileStorage fileStorage = storageManager.getOrCreateFileStorage(sourceStorage);
            FileRef fileRef = tempStorage.putFileIntoStorage(fileId, fileName, fileStorage);

            FileDescriptor fileDescriptor = dataManager.create(FileDescriptor.class);
            fileDescriptor.setId(UUID.randomUUID());
            fileDescriptor.setName(uniqueName);
            fileDescriptor.setSize(contentLength);
            if (fileName.contains(".")) {
                fileDescriptor.setExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
            }
            fileDescriptor.setLastModified(LocalDateTime.now());
            fileDescriptor.setFolder(folder);
            fileDescriptor.setFileRef(fileRef);
            fileDescriptor.setSourceStorage(sourceStorage);
            fileDescriptor.setInTrash(false);
            fileDescriptor.setCreateBy(username);
            FileDescriptor savedDescriptor = dataManager.save(fileDescriptor);

            File fileForOcr = resolveFileForOcr(tempFile, ocrTempCopy, fileName);
//            if (fileForOcr != null) {
//                ocrFileTextSearchService.indexFile(savedDescriptor, fileForOcr);
//            } else {
//                log.debug("Skip OCR for {} because source file is not available anymore", fileName);
//            }

            return savedDescriptor;

        } catch (NoSuchKeyException e) {
            throw new RuntimeException("Tệp không tồn tại trên S3: " + fileName, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                throw new RuntimeException("Lỗi xác thực S3: Sai Access key hoặc Secret access key.");
            }
            if (e.statusCode() == 404) {
                throw new RuntimeException("Bucket không tồn tại: " + sourceStorage.getBucket(), e);
            }
            throw new RuntimeException("Lỗi S3 (" + e.statusCode() + "): " + e.getMessage(), e);
        } finally {
            cleanupTempFile(ocrTempCopy);
        }
    }

    public byte[] downloadFile(FileDescriptor fileDescriptor) {
        FileStorage fileStorage = storageManager.getOrCreateFileStorage(fileDescriptor.getSourceStorage());

        User user = (User) currentAuthentication.getUser();
        boolean permission = permissionService.hasPermission(user, PermissionType.READ, fileDescriptor);
        if(!permission){
            throw new AccessDeniedException(
                    "FILE",
                    fileDescriptor.getId().toString(),
                    "READ"
            );
        }
        try (InputStream inputStream = fileStorage.openStream(fileDescriptor.getFileRef())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tải xuống: " + e.getMessage(), e);
        }
    }

    private File duplicateTempFile(File original, String fileName) {
        if (original == null || !original.exists()) {
            return null;
        }
        try {
            String suffix = "";
            if (fileName != null && fileName.contains(".")) {
                suffix = fileName.substring(fileName.lastIndexOf('.'));
            }
            Path copyPath = Files.createTempFile("ecm-ocr-", suffix);
            Files.copy(original.toPath(), copyPath, StandardCopyOption.REPLACE_EXISTING);
            return copyPath.toFile();
        } catch (IOException e) {
            log.warn("Unable to duplicate temp file for OCR, skipping copy", e);
            return null;
        }
    }

    private File resolveFileForOcr(File original, File duplicated, String fileName) {
        if (duplicated != null && duplicated.exists()) {
            return duplicated;
        }
        if (original != null && original.exists()) {
            return original;
        }
        log.debug("Original temp file for {} has already been removed", fileName);
        return null;
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile.toPath());
        } catch (IOException e) {
            log.warn("Failed to delete OCR temp file {}", tempFile, e);
        }
    }

}
