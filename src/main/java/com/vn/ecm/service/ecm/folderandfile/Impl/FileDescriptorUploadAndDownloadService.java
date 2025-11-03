package com.vn.ecm.service.ecm.folderandfile.Impl;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.flowui.upload.TemporaryStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileDescriptorUploadAndDownloadService implements IFileDescriptorUploadAndDownloadService {
    @Autowired
    private DataManager dataManager;
    @Autowired
    private TemporaryStorage tempStorage;
    @Autowired
    private DynamicStorageManager storageManager;
    @Autowired
    private FileDescriptorService fileDescriptorService;

    public FileDescriptor uploadFile(UUID fileId,
                                     String fileName,
                                     Long contentLength,
                                     Folder folder,
                                     SourceStorage sourceStorage,
                                     String username){
        try {
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
            return dataManager.save(fileDescriptor);

        }catch(NoSuchKeyException e){
            throw new RuntimeException("Tệp không tồn tại trên S3: " + fileName, e);
        }catch (S3Exception e) {
            if (e.statusCode() == 403) {
                throw new RuntimeException("Lỗi xác thực S3: Sai Access key hoặc Secret access key.");
            }
            if (e.statusCode() == 404) {
                throw new RuntimeException("Bucket không tồn tại: " + sourceStorage.getBucket(), e);
            }
            throw new RuntimeException("Lỗi S3 (" + e.statusCode() + "): " + e.getMessage(), e);
        }
    }


    public byte[] downloadFile(FileDescriptor fileDescriptor) {
        FileStorage fileStorage = storageManager.getOrCreateFileStorage(fileDescriptor.getSourceStorage());
        try (InputStream inputStream = fileStorage.openStream(fileDescriptor.getFileRef())) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tải xuống: " + e.getMessage(), e);
        }
    }

}
