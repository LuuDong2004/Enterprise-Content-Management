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
    private final SourceStorage sourceStorage;

    public ZipEntrySourceDto(FileDescriptor fileDescriptor, String path, SourceStorage sourceStorage) {
        this.fileDescriptor = fileDescriptor;
        this.path = path;
        this.sourceStorage = sourceStorage;
    }


    public FileDescriptor getFileDescriptor() {
        return fileDescriptor;
    }

    public String getPath() {
        return path;
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage;
    }
}
