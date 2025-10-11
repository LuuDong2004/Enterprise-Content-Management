package com.vn.ecm.ecm.storage.s3;

import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class S3Storage implements FileStorage {

    private final SourceStorage source;
    private final S3Client s3;
    private final String storageName; // cố định cho FileRef

    public S3Storage(SourceStorage source, S3Client s3) {
        this.source = Objects.requireNonNull(source);
        this.s3 = Objects.requireNonNull(s3);
        this.storageName = "s3-" + ( // ưu tiên code nếu bạn có field đó; nếu chưa có, dùng id
                source.getId() != null ? source.getId().toString() : UUID.randomUUID()
        );
    }

    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public FileRef saveStream(String fileName, InputStream inputStream, Map<String, Object> parameters) {
        String key = buildKey(fileName);
        try {
            ensureBucketExists();
            Long contentLength = parameters != null ? (Long) parameters.get("contentLength") : null;

            if (contentLength != null && contentLength >= 0) {
                s3.putObject(PutObjectRequest.builder()
                                .bucket(source.getBucket())
                                .key(key)
                                .build(),
                        RequestBody.fromInputStream(inputStream, contentLength));
            } else {
                byte[] all = inputStream.readAllBytes();
                s3.putObject(PutObjectRequest.builder()
                                .bucket(source.getBucket())
                                .key(key)
                                .build(),
                        RequestBody.fromBytes(all));
            }
            return new FileRef(storageName, key, fileName);
        } catch (S3Exception e) {
            String details = "Upload failed to bucket " + source.getBucket() + " key " + key
                    + " | status=" + e.statusCode()
                    + " code=" + e.awsErrorDetails().errorCode()
                    + " reqId=" + e.requestId();
            throw new RuntimeException(details, e);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed to bucket " + source.getBucket() + " key " + key, e);
        }
    }

    @Override
    public InputStream openStream(FileRef reference) {
        assertSameStorage(reference);
        try {
            var rsp = s3.getObject(GetObjectRequest.builder()
                            .bucket(source.getBucket())
                            .key(reference.getPath())
                            .build(),
                    software.amazon.awssdk.core.sync.ResponseTransformer.toBytes());
            return new ByteArrayInputStream(rsp.asByteArray());
        } catch (NoSuchKeyException e) {
            throw new RuntimeException("Object not found: " + reference.getPath(), e);
        } catch (Exception e) {
            throw new RuntimeException("Open stream failed for: " + reference, e);
        }
    }

    @Override
    public void removeFile(FileRef reference) {
        assertSameStorage(reference);
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(source.getBucket())
                    .key(reference.getPath())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Delete failed for: " + reference, e);
        }
    }

    @Override
    public boolean fileExists(FileRef reference) {
        assertSameStorage(reference);
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(source.getBucket())
                    .key(reference.getPath())
                    .build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    private void assertSameStorage(FileRef ref) {
        if (!storageName.equals(ref.getStorageName())) {
            throw new IllegalArgumentException("FileRef.storageName=" + ref.getStorageName()
                    + " không khớp với " + storageName);
        }
    }

    private String buildKey(String fileName) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            ext = fileName.substring(dot);
        }
        LocalDate d = LocalDate.now();
        return d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/"
                + UUID.randomUUID() + ext;
    }

    private void ensureBucketExists() {
        String bucket = source.getBucket();
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception ce) {
                // If another node created it concurrently or provider forbids create, ignore if now exists
                try {
                    s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                } catch (Exception ignored) {
                    throw ce; // rethrow original create error if still not available
                }
            }
        }
    }
}
