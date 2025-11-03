package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Table(name = "SOURCE_STORAGE")
@Entity
@JmixEntity
public class SourceStorage {
    @JmixGeneratedValue
    @Id
    @Column(name = "ID", nullable = false)
    private UUID id;

    @Column(name = "ACCESS_KEY")
    private String accessKey;

    @Column(name = "SECRET_ACCESS_KEY")
    private String secretAccessKey;

    @Column(name = "REGION")
    private String region;

    @Column(name = "BUCKET")
    private String bucket;

    @Column(name = "CHUNK_SIZE")
    private String chunkSize;

    @Column(name = "ENDPOINT_URL")
    private String endpointUrl;

    @Column(name = "TYPE")
    //@Enumerated(EnumType.STRING)
    private String type;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @Column(name = "ACTIVE")
    private Boolean active;

    @Column(name = "WEB_ROOT_PATH")
    private String webRootPath;

    @Column(name = "USE_PATH_STYLE")
    private Boolean usePathStyleBucketAddressing;

    public String getName() {
        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public String getWebRootPath() {
        return webRootPath;
    }

    public void setWebRootPath(String webRootPath) {
        this.webRootPath = webRootPath;
    }

    public void setType(StorageType type) {
        this.type = type == null ? null : type.getId();
    }

    public StorageType getType() {
        return type == null ? null : StorageType.fromId(type);
    }

    public Boolean getUsePathStyleBucketAddressing() {
        return usePathStyleBucketAddressing;
    }

    public void setUsePathStyleBucketAddressing(Boolean usePathStyleBucketAddressing) {
        this.usePathStyleBucketAddressing = usePathStyleBucketAddressing;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket.toLowerCase();
    }

    public String getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(String chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}