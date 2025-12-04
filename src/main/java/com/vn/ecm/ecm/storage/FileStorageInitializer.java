package com.vn.ecm.ecm.storage;

import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Khởi tạo các kho lưu trữ động (S3, WebDirectory, FTP) khi ứng dụng khởi động.
 */
@Component
public class FileStorageInitializer {

    private static final Logger log = LoggerFactory.getLogger(FileStorageInitializer.class);

    private final DataManager dataManager;
    private final DynamicStorageManager dynamicStorageManager;
    private final SystemAuthenticator systemAuthenticator;

    public FileStorageInitializer(DataManager dataManager,
                                  DynamicStorageManager dynamicStorageManager,
                                  SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.dynamicStorageManager = dynamicStorageManager;
        this.systemAuthenticator = systemAuthenticator;
    }
    @EventListener(ContextRefreshedEvent.class)
    public void onAppStarted() {
        systemAuthenticator.withSystem(() -> {
            List<SourceStorage> activeStorages = dataManager.load(SourceStorage.class)
                    .query("select s from SourceStorage s where s.active = true")
                    .list();
            log.info("Phát hiện {} kho lưu trữ đang bật (active).", activeStorages.size());
            for (SourceStorage sourceStorage : activeStorages) {
                try {
                    if (dynamicStorageManager.isStorageValid(sourceStorage)) {
                        dynamicStorageManager.getOrCreateFileStorage(sourceStorage);
                        log.info("Khởi tạo kho lưu trữ thành công: id={}, loại={}",
                                sourceStorage.getId(), sourceStorage.getType());
                    } else {
                        log.warn("Kho lưu trữ không hợp lệ, bỏ qua: id={}, loại={}",
                                sourceStorage.getId(), sourceStorage.getType());
                    }
                } catch (Exception e) {
                    log.error("Không thể khởi tạo kho lưu trữ id=" + sourceStorage.getId()
                                    + ", loại=" + sourceStorage.getType()
                                    + ". Lỗi: " + e.getMessage(),
                            e);
                }
            }
            log.info("Khởi tạo kho lưu trữ động hoàn tất.");
            return null;
        });
    }
}
