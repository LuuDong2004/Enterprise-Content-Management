package com.vn.ecm.ecm.storage;

import com.vn.ecm.ecm.storage.web_directory.WebDirectoryStorage;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import com.vn.ecm.ecm.storage.s3.S3ClientFactory;
import com.vn.ecm.ecm.storage.s3.S3Storage;

import io.jmix.core.FileStorage;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DynamicStorageManager {

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

        beanFactory.registerSingleton(beanName, storage);
        idToBeanName.put(s.getId().toString(), beanName);
        return storage;
    }

    public FileStorage refresh(SourceStorage s) {
        destroy(s);
        return getOrCreate(s);
    }

    public void destroy(SourceStorage s) {
        String beanName = idToBeanName.remove(s.getId().toString());
        if (beanName != null && beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
    }
}
