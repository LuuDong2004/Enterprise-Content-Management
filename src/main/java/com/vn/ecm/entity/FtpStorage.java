package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@JmixEntity
@Table(name = "FTP_STORAGE")
@Entity
public class FtpStorage extends SourceStorage{
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;



    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}