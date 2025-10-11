package com.vn.ecm.ecm.storage;

import com.vn.ecm.ecm.storage.web_directory.WebDirectoryStorage;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.ecm.storage.s3.S3Storage;

import io.jmix.core.DataManager;
import io.jmix.core.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DynamicStorageManager {

    private static final Logger log = LoggerFactory.getLogger(DynamicStorageManager.class);
    @Autowired
    private DataManager dataManager;

    private final DefaultListableBeanFactory beanFactory;
    private final S3ClientFactory s3ClientFactory = new S3ClientFactory();
    private final ConcurrentMap<String, String> idToBeanName = new ConcurrentHashMap<>();

    public DynamicStorageManager(ApplicationContext ctx) {
        this.beanFactory = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
    }

    private String storageNameOf(SourceStorage s) {
        String prefix = s.getType() == StorageType.WEBDIR ? "webdir-" : "s3-";
        return prefix + Objects.requireNonNull(s.getId());
    }

    private String beanNameOf(SourceStorage s) {
        return "fs_" + storageNameOf(s);
    }

    public boolean supports(SourceStorage s) {
        if (!Boolean.TRUE.equals(s.getActive())) return false;
        if (s.getType() == StorageType.S3) {
            return s.getBucket() != null && !s.getBucket().isBlank();
        } else if (s.getType() == StorageType.WEBDIR) {
            return s.getWebRootPath() != null && !s.getWebRootPath().isBlank();
        }
        return false;
    }

    public FileStorage getOrCreate(SourceStorage s) {
        if (!supports(s)) {
            throw new IllegalArgumentException("SourceStorage không hợp lệ/không active: " + s.getId());
        }
        String beanName = beanNameOf(s);
        String storageName = storageNameOf(s);
        
        if (beanFactory.containsBean(beanName)) {
            return beanFactory.getBean(beanName, FileStorage.class);
        }

        FileStorage storage;
        if (s.getType() == StorageType.S3) {
            S3Client client = s3ClientFactory.create(s);
            storage = new S3Storage(s, client);
        } else if (s.getType() == StorageType.WEBDIR) {
            storage = new WebDirectoryStorage(s);
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + s.getType());
        }

        // Đăng ký với cả hai tên: với prefix fs_ và không có prefix
        log.info("Registering storage with bean name: {}", beanName);
        beanFactory.registerSingleton(beanName, storage);  // fs_webdir-xxx
        
        log.info("Registering storage with storage name: {}", storageName);
        beanFactory.registerSingleton(storageName, storage); // webdir-xxx (cho FileStorageLocator)
        
        idToBeanName.put(s.getId().toString(), beanName);
        
        log.info("Storage registration completed. Both names registered successfully.");
        return storage;
    }

    public FileStorage refresh(SourceStorage s) {
        destroy(s);
        return getOrCreate(s);
    }

    public void destroy(SourceStorage s) {
        String beanName = idToBeanName.remove(s.getId().toString());
        String storageName = storageNameOf(s);
        
        if (beanName != null && beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
        
        // Cũng xóa bean với tên không có prefix fs_
        if (beanFactory.containsSingleton(storageName)) {
            beanFactory.destroySingleton(storageName);
        }
    }
    public void ensureRegistered(String storageName) {
        log.info("=== ENSURING STORAGE REGISTERED ===");
        log.info("Storage name: {}", storageName);
        
        if (storageName == null || storageName.isBlank()) {
            log.warn("Storage name is null or blank");
            return;
        }

        String beanName = "fs_" + storageName; // Jmix Locator dùng tên này
        log.info("Bean name: {}", beanName);
        
        if (beanFactory.containsBean(beanName)) {
            log.info("Storage bean already exists: {}", beanName);
            // Đảm bảo storage cũng được đăng ký với tên không có prefix fs_
            if (!beanFactory.containsBean(storageName)) {
                FileStorage existingStorage = beanFactory.getBean(beanName, FileStorage.class);
                beanFactory.registerSingleton(storageName, existingStorage);
                log.info("Also registered storage with name: {}", storageName);
            }
            return;
        }

        int dash = storageName.indexOf('-');
        if (dash <= 0 || dash == storageName.length() - 1) {
            log.error("Invalid storage name format: {}", storageName);
            return;
        }

        String prefix = storageName.substring(0, dash);      // "s3" | "webdir"
        String idStr  = storageName.substring(dash + 1);
        log.info("Parsed prefix: {}, ID string: {}", prefix, idStr);

        UUID id;
        try { 
            id = UUID.fromString(idStr); 
            log.info("Parsed UUID: {}", id);
        } catch (Exception e) { 
            log.error("Failed to parse UUID from: {}", idStr, e);
            return; 
        }

        SourceStorage s = dataManager.load(SourceStorage.class).id(id).optional().orElse(null);
        if (s == null) {
            log.error("SourceStorage not found with ID: {}", id);
            return;
        }
        
        if (!Boolean.TRUE.equals(s.getActive())) {
            log.error("SourceStorage is not active: {}", id);
            return;
        }
        
        log.info("Found SourceStorage: ID={}, Type={}, Active={}", 
                s.getId(), s.getType(), s.getActive());

        boolean ok = ("s3".equals(prefix) && s.getType() == StorageType.S3)
                || ("webdir".equals(prefix) && s.getType() == StorageType.WEBDIR);
        
        log.info("Type validation result: {}", ok);
        
        if (ok) {
            log.info("Creating and registering storage...");
            try {
                getOrCreate(s); // đăng ký singleton fs_{storageName} và storageName
                log.info("Storage registered successfully with bean name: {}", beanName);
                log.info("Storage also registered with storage name: {}", storageName);
                
                // Verify both registrations
                if (beanFactory.containsBean(beanName)) {
                    log.info("Verified: Bean with prefix fs_ exists: {}", beanName);
                } else {
                    log.error("ERROR: Bean with prefix fs_ NOT found: {}", beanName);
                }
                
                if (beanFactory.containsBean(storageName)) {
                    log.info("Verified: Storage without prefix exists: {}", storageName);
                } else {
                    log.error("ERROR: Storage without prefix NOT found: {}", storageName);
                }
            } catch (Exception e) {
                log.error("Failed to register storage", e);
            }
        } else {
            log.error("Storage type mismatch: prefix={}, actual type={}", prefix, s.getType());
        }
        
        log.info("=== STORAGE REGISTRATION COMPLETED ===");
    }

}
