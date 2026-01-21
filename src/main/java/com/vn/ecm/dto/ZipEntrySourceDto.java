package com.vn.ecm.dto;


import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.metamodel.annotation.JmixEntity;

/**
 * Build full path file descriptor
 */
public class ZipEntrySourceDto {

    private final FileDescriptor fileDescriptor;
    private final String path;


    public ZipEntrySourceDto(FileDescriptor fileDescriptor, String path) {
        this.fileDescriptor = fileDescriptor;
        this.path = path;
    }


    public FileDescriptor getFileDescriptor() {
        return fileDescriptor;
    }

    public String getPath() {
        return path;
    }
}
