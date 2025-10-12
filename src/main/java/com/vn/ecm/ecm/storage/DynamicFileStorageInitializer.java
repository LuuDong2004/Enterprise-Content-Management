package com.vn.ecm.ecm.storage;

import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.List;

@Component
public class DynamicFileStorageInitializer {

    private final DataManager dataManager;
    private final DynamicStorageManager s3Manager;
    private final SystemAuthenticator systemAuthenticator;

    public DynamicFileStorageInitializer(DataManager dataManager, DynamicStorageManager s3Manager, SystemAuthenticator systemAuthenticator) {
        this.dataManager = dataManager;
        this.s3Manager = s3Manager;
        this.systemAuthenticator = systemAuthenticator;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onAppStarted() {
        systemAuthenticator.withSystem(() -> {
            List<SourceStorage> actives = dataManager.load(SourceStorage.class)
                    .query("select s from SourceStorage s where s.active = true")
                    .list();

            for (SourceStorage s : actives) {
                if (s.getType() == StorageType.S3) {
                    try {
                        s3Manager.getOrCreateFileStorage(s);
                    } catch (Exception e) {
                        // log lại, không chặn app start
                        System.err.println("Cannot init storage for " + s.getId() + ": " + e.getMessage());
                    }
                }
            }
            return null;
        });
    }
}
