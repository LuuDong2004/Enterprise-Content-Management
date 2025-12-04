package com.vn.ecm.service.ecm.folderandfile.Impl;

import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.upload.TemporaryStorage;
import org.springframework.stereotype.Service;


@Service
public class FilePasswordZipService {

    private final FileStorageLocator fileStorageLocator;
    private final TemporaryStorage temporaryStorage;


    // TTL (ms) – ví dụ 30 phút
    private static final long TTL_MILLIS = 30L * 60 * 1000;

    public FilePasswordZipService(FileStorageLocator fileStorageLocator, TemporaryStorage temporaryStorage) {
        this.fileStorageLocator = fileStorageLocator;
        this.temporaryStorage = temporaryStorage;
    }

}
