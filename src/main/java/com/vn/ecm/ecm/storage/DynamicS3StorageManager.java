package com.vn.ecm.ecm.storage;

import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.entity.StorageType;
import io.jmix.core.FileStorage;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DynamicS3StorageManager {

    private final DefaultListableBeanFactory beanFactory;
    private final S3ClientFactory clientFactory = new S3ClientFactory();
    private final ConcurrentMap<String, String> idToBeanName = new ConcurrentHashMap<>();

    public DynamicS3StorageManager(ApplicationContext ctx) {
        this.beanFactory = (DefaultListableBeanFactory) ctx.getAutowireCapableBeanFactory();
    }

    private String storageNameOf(SourceStorage s) {
        // Nếu có s.getCode() thì thay vào đây cho đẹp
        return "s3-" + Objects.requireNonNull(s.getId()).toString();
    }

    private String beanNameOf(SourceStorage s) {
        return "fs_" + storageNameOf(s); // convention Jmix: fs_{storageName}
    }

    public boolean supports(SourceStorage s) {
        return Boolean.TRUE.equals(s.getActive())
                && s.getBucket() != null && !s.getBucket().isBlank();
    }

    public FileStorage getOrCreate(SourceStorage s) {
        if (!supports(s)) {
            throw new IllegalArgumentException("SourceStorage không hoạt động hoặc không phải S3");
        }
        String beanName = beanNameOf(s);

        if (beanFactory.containsBean(beanName)) {
            return beanFactory.getBean(beanName, FileStorage.class);
        }

        S3Client client = clientFactory.create(s);
        S3Storage storage = new S3Storage(s, client);

        beanFactory.registerSingleton(beanName, storage);
        idToBeanName.put(s.getId().toString(), beanName);
        return storage;
    }

    /** Dùng khi bạn UPDATE cấu hình nguồn: destroy + tạo lại. */
    public FileStorage refresh(SourceStorage s) {
        destroy(s);
        return getOrCreate(s);
    }

    /** Gỡ bean khi nguồn bị inactive/xoá. */
    public void destroy(SourceStorage s) {
        String beanName = idToBeanName.remove(s.getId().toString());
        if (beanName != null && beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
        }
    }
}
