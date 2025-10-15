package com.vn.ecm.ecm.storage;

import com.vn.ecm.ecm.storage.web_directory.WebDirectoryStorage;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.ecm.storage.s3.S3Storage;

import io.jmix.core.DataManager;
import io.jmix.core.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Quản lý các storage động (S3, WebDirectory) được tạo runtime
 * Đăng ký storage vào Spring context để Jmix có thể sử dụng
 */
@Component
public class DynamicStorageManager {
    @Autowired
    private DataManager dataManager;

    private final DefaultListableBeanFactory springBeanFactory;
    private final S3ClientFactory s3ClientFactory = new S3ClientFactory();
    private final ConcurrentMap<String, String> storageIdToBeanNameMap = new ConcurrentHashMap<>();

    public DynamicStorageManager(ApplicationContext applicationContext) {
        this.springBeanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    }

    /**
     * Tạo tên storage cho FileRef (ví dụ: webdir-uuid, s3-uuid)
     */
    private String generateStorageName(SourceStorage sourceStorage) {
        String storageTypePrefix = sourceStorage.getType() == StorageType.WEBDIR ? "webdir-" : "s3-";
        return storageTypePrefix + Objects.requireNonNull(sourceStorage.getId());
    }

    /**
     * Tạo tên bean trong Spring context (ví dụ: fs_webdir-uuid)
     */
    private String generateBeanName(SourceStorage sourceStorage) {
        return "fs_" + generateStorageName(sourceStorage);
    }

    /**
     * Kiểm tra xem SourceStorage có hợp lệ và có thể sử dụng được không
     */
    public boolean isStorageValid(SourceStorage sourceStorage) {
        if (!Boolean.TRUE.equals(sourceStorage.getActive())) {
            return false;
        }
        
        if (sourceStorage.getType() == StorageType.S3) {
            return sourceStorage.getBucket() != null && !sourceStorage.getBucket().isBlank();
        } else if (sourceStorage.getType() == StorageType.WEBDIR) {
            return sourceStorage.getWebRootPath() != null && !sourceStorage.getWebRootPath().isBlank();
        }
        return false;
    }

    /**
     * Lấy hoặc tạo mới FileStorage instance cho SourceStorage
     * Nếu đã tồn tại trong Spring context thì lấy ra, nếu chưa thì tạo mới và đăng ký
     */
    public FileStorage getOrCreateFileStorage(SourceStorage sourceStorage) {
        if (!isStorageValid(sourceStorage)) {
            throw new IllegalArgumentException("SourceStorage không hợp lệ hoặc không active: " + sourceStorage.getId());
        }
        
        String beanName = generateBeanName(sourceStorage);
        String storageName = generateStorageName(sourceStorage);
        
        // Nếu đã tồn tại trong Spring context thì lấy ra
        if (springBeanFactory.containsBean(beanName)) {
            return springBeanFactory.getBean(beanName, FileStorage.class);
        }

        // Tạo mới FileStorage instance
        FileStorage fileStorageInstance = createFileStorageInstance(sourceStorage);

        // Đăng ký vào Spring context với cả hai tên để Jmix có thể tìm thấy
        springBeanFactory.registerSingleton(beanName, fileStorageInstance);      // fs_webdir-xxx
        springBeanFactory.registerSingleton(storageName, fileStorageInstance);   // webdir-xxx (cho Jmix FileStorageLocator)
        storageIdToBeanNameMap.put(sourceStorage.getId().toString(), beanName);
        
        return fileStorageInstance;
    }

    /**
     * Tạo FileStorage instance dựa trên loại storage
     */
    private FileStorage createFileStorageInstance(SourceStorage sourceStorage) {
        if (sourceStorage.getType() == StorageType.S3) {
            S3Client s3Client = s3ClientFactory.create(sourceStorage);
            return new S3Storage(sourceStorage, s3Client);
        } else if (sourceStorage.getType() == StorageType.WEBDIR) {
            return new WebDirectoryStorage(sourceStorage);
        } else {
            throw new UnsupportedOperationException("Loại storage không được hỗ trợ: " + sourceStorage.getType());
        }
    }

    /**
     * Làm mới FileStorage instance (xóa cũ và tạo mới)
     */
    public FileStorage refreshFileStorage(SourceStorage sourceStorage) {
        removeFileStorageFromContext(sourceStorage);
        return getOrCreateFileStorage(sourceStorage);
    }

    /**
     * Xóa FileStorage khỏi Spring context
     */
    public void removeFileStorageFromContext(SourceStorage sourceStorage) {
        String beanName = storageIdToBeanNameMap.remove(sourceStorage.getId().toString());
        String storageName = generateStorageName(sourceStorage);

        // Xóa bean với prefix fs_
        if (beanName != null && springBeanFactory.containsSingleton(beanName)) {
            springBeanFactory.destroySingleton(beanName);
        }

        // Xóa bean không có prefix (cho Jmix FileStorageLocator)
        if (springBeanFactory.containsSingleton(storageName)) {
            springBeanFactory.destroySingleton(storageName);
        }
    }
    /**
     * Đảm bảo storage được đăng ký trong Spring context
     * Được gọi khi Jmix cần tìm storage theo tên
     */
    public void ensureStorageRegistered(String storageName) {
        if (storageName == null || storageName.isBlank()) {
            return;
        }

        String beanName = "fs_" + storageName;
        
        // Nếu đã tồn tại, đảm bảo cũng có tên không prefix cho Jmix
        if (springBeanFactory.containsBean(beanName)) {
            if (!springBeanFactory.containsBean(storageName)) {
                FileStorage existingStorage = springBeanFactory.getBean(beanName, FileStorage.class);
                springBeanFactory.registerSingleton(storageName, existingStorage);
            }
            return;
        }

        // Parse storage name để lấy thông tin
        StorageNameInfo nameInfo = parseStorageName(storageName);
        if (nameInfo == null) {
            return;
        }

        // Tìm SourceStorage trong database
        SourceStorage sourceStorage = dataManager.load(SourceStorage.class)
                .id(nameInfo.storageId)
                .optional()
                .orElse(null);
                
        if (sourceStorage == null || !Boolean.TRUE.equals(sourceStorage.getActive())) {
            return;
        }

        // Kiểm tra loại storage có khớp không
        boolean isTypeMatch = (StorageType.S3.equals(nameInfo.storageType) && sourceStorage.getType() == StorageType.S3)
                || (StorageType.WEBDIR.equals(nameInfo.storageType) && sourceStorage.getType() == StorageType.WEBDIR);
        
        if (isTypeMatch) {
            getOrCreateFileStorage(sourceStorage);
        }
    }

    /**
     * Parse storage name để lấy thông tin loại và ID
     */
    private StorageNameInfo parseStorageName(String storageName) {
        int dashIndex = storageName.indexOf('-');
        if (dashIndex <= 0 || dashIndex == storageName.length() - 1) {
            return null;
        }

        String storageTypeString = storageName.substring(0, dashIndex);  // "s3" | "webdir"
        String storageIdString = storageName.substring(dashIndex + 1);

        try {
            UUID storageId = UUID.fromString(storageIdString);
            StorageType storageType = "s3".equals(storageTypeString) ? StorageType.S3 : StorageType.WEBDIR;
            return new StorageNameInfo(storageId, storageType);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Thông tin được parse từ storage name
     */
    private static class StorageNameInfo {
        final UUID storageId;
        final StorageType storageType;

        StorageNameInfo(UUID storageId, StorageType storageType) {
            this.storageId = storageId;
            this.storageType = storageType;
        }
    }



}

