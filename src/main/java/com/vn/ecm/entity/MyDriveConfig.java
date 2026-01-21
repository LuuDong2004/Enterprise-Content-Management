package com.vn.ecm.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Table(name = "MY_DRIVE_CONFIG", indexes = {
        @Index(name = "IDX_MY_DRIVE_CONFIG_OWNER", columnList = "OWNER_ID", unique = true),
        @Index(name = "IDX_MY_DRIVE_CONFIG_MY_DRIVE", columnList = "MY_DRIVE_ID", unique = true)
})
@Entity
public class MyDriveConfig {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "OWNER_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @JoinColumn(name = "MY_DRIVE_ID")
    @ManyToOne(fetch = FetchType.LAZY)
    private Folder myDrive;

    public Folder getMyDrive() {
        return myDrive;
    }

    public void setMyDrive(Folder myDrive) {
        this.myDrive = myDrive;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}