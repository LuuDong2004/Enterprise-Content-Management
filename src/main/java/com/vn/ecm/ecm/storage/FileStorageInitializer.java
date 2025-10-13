package com.vn.ecm.ecm.storage;

import com.vn.ecm.entity.SourceStorage;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.context.event.ContextRefreshedEvent;
import java.util.List;
// khởi tạo các kho lưu trữ động (s3,web directory) khi app start
@Component
public class FileStorageInitializer {

    private final DataManager dataManager;
    private final DynamicStorageManager storageManager;

    private final SystemAuthenticator systemAuthenticator;

    public FileStorageInitializer(DataManager dataManager, DynamicStorageManager s3Manager, SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.storageManager = s3Manager;
        this.systemAuthenticator = systemAuthenticator;
    }

    // khởi tạo các kho lưu trữ động (s3,web directory) khi app start
    @EventListener(ContextRefreshedEvent.class)
    public void onAppStarted() {
        systemAuthenticator.withSystem(() -> {
            List<SourceStorage> actives = dataManager.load(SourceStorage.class)
                    .query("select s from SourceStorage s where s.active = true")
                    .list();
            for (SourceStorage sourceStorage : actives) {
                if (findSourceStorage(sourceStorage)) {
                    try {
                        storageManager.getOrCreateFileStorage(sourceStorage);
                    } catch (Exception e) {
                        // Không chặn app start, chỉ log lỗi
                        System.err.println("Cannot init storage for " + sourceStorage.getId() + ": " + e.getMessage());
                    }
                }
            }
            return null;
        });

    }
    public boolean findSourceStorage(SourceStorage sourceStorage) {
        return storageManager.isStorageValid(sourceStorage);
    }

}
