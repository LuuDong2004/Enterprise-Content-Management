package com.vn.ecm.ecm.storage;

import com.vn.ecm.ecm.storage.ftp.FtpStorage;
import com.vn.ecm.ecm.storage.ftp.config.FtpStorageConfig;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.ecm.storage.s3.S3Storage;
import com.vn.ecm.ecm.storage.web_directory.WebDirectoryStorage;
import com.vn.ecm.entity.FtpStorageEntity;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Quản lý các storage động (S3, WebDirectory, FTP) được tạo runtime.
 * Đăng ký storage vào Spring context để Jmix có thể sử dụng.
 */
@Component
public class DynamicStorageManager {

    private final DataManager dataManager;
    private final DefaultListableBeanFactory springBeanFactory;
    private final S3ClientFactory s3ClientFactory;
    private final FileStorageLocator fileStorageLocator;

    /**
     * Map SourceStorage.id -> beanName (fs_xxx-uuid)
     */
    private final ConcurrentMap<UUID, String> storageIdToBeanNameMap = new ConcurrentHashMap<>();

    public DynamicStorageManager(ApplicationContext applicationContext,
                                 DataManager dataManager,
                                 S3ClientFactory s3ClientFactory,
                                 FileStorageLocator fileStorageLocator) {
        this.springBeanFactory =
                (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        this.dataManager = dataManager;
        this.s3ClientFactory = s3ClientFactory;
        this.fileStorageLocator = fileStorageLocator;
    }

    // =====================================================================
    // Helper: tên storage & tên bean
    // =====================================================================

    /**
     * Tạo tên storage cho FileRef (ví dụ: s3-uuid, webdir-uuid, ftp-uuid)
     */
    private String generateStorageName(SourceStorage sourceStorage) {
        String storageTypePrefix;
        if (sourceStorage.getType() == StorageType.S3) {
            storageTypePrefix = "s3-";
        } else if (sourceStorage.getType() == StorageType.WEBDIR) {
            storageTypePrefix = "webdir-";
        } else if (sourceStorage.getType() == StorageType.FTP) {
            storageTypePrefix = "ftp-";
        } else {
            throw new UnsupportedOperationException(
                    "Loại storage không được hỗ trợ: " + sourceStorage.getType());
        }
        return storageTypePrefix + Objects.requireNonNull(sourceStorage.getId());
    }

    /**
     * Tạo tên bean trong Spring context (ví dụ: fs_s3-uuid)
     */
    private String generateBeanName(SourceStorage sourceStorage) {
        return "fs_" + generateStorageName(sourceStorage);
    }

    // =====================================================================
    // Kiểm tra cấu hình hợp lệ
    // =====================================================================

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

        } else if (sourceStorage.getType() == StorageType.FTP) {
            FtpStorageEntity ftp = loadFtpStorageEntity(sourceStorage.getId());
            if (ftp == null) {
                return false;
            }
            return ftp.getHost() != null && !ftp.getHost().isBlank()
                    && ftp.getUsername() != null && !ftp.getUsername().isBlank()
                    && ftp.getPassword() != null && !ftp.getPassword().isBlank();
        }

        return false;
    }

    // =====================================================================
    // Lấy / tạo FileStorage instance (dynamic)
    // =====================================================================

    /**
     * Lấy hoặc tạo mới FileStorage instance cho SourceStorage (dynamic)
     */
    public FileStorage getOrCreateFileStorage(SourceStorage sourceStorage) {
        String beanName = generateBeanName(sourceStorage);
        String storageName = generateStorageName(sourceStorage);

        // Nếu đã tồn tại trong Spring context thì lấy ra
        if (springBeanFactory.containsBean(beanName)) {
            return springBeanFactory.getBean(beanName, FileStorage.class);
        }

        // Tạo mới FileStorage instance
        FileStorage fileStorageInstance = createFileStorageInstance(sourceStorage, storageName);

        // Đăng ký vào Spring context với cả hai tên để Jmix có thể tìm thấy
        springBeanFactory.registerSingleton(beanName, fileStorageInstance);      // fs_xxx
        springBeanFactory.registerSingleton(storageName, fileStorageInstance);   // xxx (cho Jmix FileStorageLocator)
        storageIdToBeanNameMap.put(sourceStorage.getId(), beanName);

        return fileStorageInstance;
    }

    /**
     * Tạo FileStorage instance dựa trên loại storage
     */
    private FileStorage createFileStorageInstance(SourceStorage sourceStorage, String storageName) {
        if (sourceStorage.getType() == StorageType.S3) {
            S3Client s3Client = s3ClientFactory.create(sourceStorage);
            return new S3Storage(sourceStorage, s3Client);

        } else if (sourceStorage.getType() == StorageType.WEBDIR) {
            return new WebDirectoryStorage(sourceStorage);

        } else if (sourceStorage.getType() == StorageType.FTP) {
            FtpStorageEntity ftpConfig = loadFtpStorageEntity(sourceStorage.getId());
            if (ftpConfig == null) {
                throw new IllegalStateException(
                        "Không tìm thấy FtpStorageEntity cho SourceStorage: " + sourceStorage.getId());
            }

            // PORT là String trong entity -> parse sang int, default 21 nếu rỗng
            int port = 21;
            if (ftpConfig.getPort() != null && !ftpConfig.getPort().isBlank()) {
                try {
                    port = Integer.parseInt(ftpConfig.getPort());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Port FTP không hợp lệ: " + ftpConfig.getPort(), e);
                }
            }

            FtpStorageConfig cfg = new FtpStorageConfig(
                    ftpConfig.getHost(),
                    port,
                    ftpConfig.getUsername(),
                    ftpConfig.getPassword(),
                    ftpConfig.getFtpPath(),
                    Boolean.TRUE.equals(ftpConfig.getPassiveMode())
            );

            return new FtpStorage(storageName, cfg);
        }

        throw new UnsupportedOperationException(
                "Loại storage không được hỗ trợ: " + sourceStorage.getType());
    }

    /**
     * Load FtpStorageEntity bằng ID (chung ID với SourceStorage vì kế thừa)
     */
    private FtpStorageEntity loadFtpStorageEntity(UUID id) {
        return dataManager.load(FtpStorageEntity.class)
                .id(id)
                .fetchPlan(fp -> {
                    fp.add("host");
                    fp.add("port");
                    fp.add("username");
                    fp.add("password");
                    fp.add("ftpPath");
                    fp.add("passiveMode");
                })
                .optional()
                .orElse(null);
    }

    // =====================================================================
    // Refresh / remove dynamic storage
    // =====================================================================

    public FileStorage refreshFileStorage(SourceStorage sourceStorage) {
        removeFileStorageFromContext(sourceStorage);
        return getOrCreateFileStorage(sourceStorage);
    }

    public void removeFileStorageFromContext(SourceStorage sourceStorage) {
        String beanName = storageIdToBeanNameMap.remove(sourceStorage.getId());
        String storageName = generateStorageName(sourceStorage);

        if (beanName != null && springBeanFactory.containsSingleton(beanName)) {
            springBeanFactory.destroySingleton(beanName);
        }

        if (springBeanFactory.containsSingleton(storageName)) {
            springBeanFactory.destroySingleton(storageName);
        }
    }

    // =====================================================================
    // Đăng ký storage theo tên (Jmix gọi) – cho dynamic
    // =====================================================================

    public void ensureStorageRegistered(String storageName) {
        if (storageName == null || storageName.isBlank()) {
            return;
        }

        String beanName = "fs_" + storageName;

        // Đã có dynamic storage với beanName fs_xxx
        if (springBeanFactory.containsBean(beanName)) {
            // Đảm bảo alias storageName cũng tồn tại
            if (!springBeanFactory.containsBean(storageName)) {
                FileStorage existingStorage =
                        springBeanFactory.getBean(beanName, FileStorage.class);
                springBeanFactory.registerSingleton(storageName, existingStorage);
            }
            return;
        }

        // storageName không map được sang dynamic (ví dụ: "fs") thì bỏ qua
        StorageNameInfo nameInfo = parseStorageName(storageName);
        if (nameInfo == null) {
            return;
        }

        SourceStorage sourceStorage = dataManager.load(SourceStorage.class)
                .id(nameInfo.storageId)
                .optional()
                .orElse(null);

        if (sourceStorage == null || !Boolean.TRUE.equals(sourceStorage.getActive())) {
            return;
        }

        boolean isTypeMatch =
                (StorageType.S3.equals(nameInfo.storageType) && sourceStorage.getType() == StorageType.S3)
                        || (StorageType.WEBDIR.equals(nameInfo.storageType) && sourceStorage.getType() == StorageType.WEBDIR)
                        || (StorageType.FTP.equals(nameInfo.storageType) && sourceStorage.getType() == StorageType.FTP);

        if (isTypeMatch) {
            getOrCreateFileStorage(sourceStorage);
        }
    }

    private StorageNameInfo parseStorageName(String storageName) {
        int dashIndex = storageName.indexOf('-');
        if (dashIndex <= 0 || dashIndex == storageName.length() - 1) {
            return null;
        }

        String storageTypeString = storageName.substring(0, dashIndex);  // "s3" | "webdir" | "ftp"
        String storageIdString = storageName.substring(dashIndex + 1);

        try {
            UUID storageId = UUID.fromString(storageIdString);
            StorageType storageType;
            switch (storageTypeString) {
                case "s3":
                    storageType = StorageType.S3;
                    break;
                case "webdir":
                    storageType = StorageType.WEBDIR;
                    break;
                case "ftp":
                    storageType = StorageType.FTP;
                    break;
                default:
                    return null;
            }
            return new StorageNameInfo(storageId, storageType);
        } catch (Exception e) {
            return null;
        }
    }

    private static class StorageNameInfo {
        final UUID storageId;
        final StorageType storageType;

        StorageNameInfo(UUID storageId, StorageType storageType) {
            this.storageId = storageId;
            this.storageType = storageType;
        }
    }

    // =====================================================================
    // API công khai
    // =====================================================================

    /**
     * Dùng cho code cũ chỉ có storageName (dynamic hoặc chuẩn).
     */
    public FileStorage getFileStorageByName(String storageName) {
        // 1. Thử dynamic
        ensureStorageRegistered(storageName);
        if (springBeanFactory.containsBean(storageName)) {
            return springBeanFactory.getBean(storageName, FileStorage.class);
        }

        // 2. Nếu không phải dynamic → fallback sang FileStorageLocator (fs, v.v.)
        return fileStorageLocator.getByName(storageName);
    }

    /**
     *  Hàm: với mọi FileRef (fs hoặc dynamic) đều resolve được FileStorage.
     * Nên dùng hàm này ở các service Preview / Download / Zip, v.v.
     */
    public FileStorage resolveFileStorage(FileRef fileRef) {
        String storageName = fileRef.getStorageName();

        ensureStorageRegistered(storageName);
        if (springBeanFactory.containsBean(storageName)) {
            return springBeanFactory.getBean(storageName, FileStorage.class);
        }
        return fileStorageLocator.getByName(storageName);
    }
}
