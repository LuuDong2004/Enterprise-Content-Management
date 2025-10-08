package com.vn.ecm.service.minio.impl;

import com.vn.ecm.service.minio.IMinioService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;


@Service
public class MinioServiceImpl implements IMinioService {
    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinioServiceImpl(
            @Value("${minio.url}") String url,
            @Value("${minio.accessKey}") String accessKey,
            @Value("${minio.secretKey}") String secretKey,
            @Value("${minio.bucket}") String defaultBucket
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
        this.defaultBucket = defaultBucket;
    }

    @Override
    public String upload(String fileRef, String fileName, InputStream inputStream, String contentType) {
        String objectName = fileRef + "/" + fileName;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket("ecm-storage")
                            .object(objectName)
                            .stream(inputStream, -1, 10 * 1024 * 1024)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build()
            );
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }
}
