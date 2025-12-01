package com.vn.ecm.entity;

import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "FTP_STORAGE")
@Entity
public class FtpStorageEntity extends SourceStorage {
    @Column(name = "HOST")
    private String host;

    @Column(name = "PORT")
    private String port;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "FTP_PATH")
    private String ftpPath;

    @Column(name = "PASSIVE_MODE")

    private Boolean passiveMode;
    public FtpStorageEntity() {
        setType(StorageType.FTP);
    }

    public Boolean getPassiveMode() {
        return passiveMode;
    }

    public void setPassiveMode(Boolean passiveMode) {
        this.passiveMode = passiveMode;
    }

    public String getFtpPath() {
        return ftpPath;
    }

    public void setFtpPath(String ftpPath) {
        this.ftpPath = ftpPath;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }


}