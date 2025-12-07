package com.vn.ecm.service.ecm.zipfile;

import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.FileRef;


import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ZipFileService {
    @Autowired
    private ZipCompressionService zipCompressionService;
    @Autowired
    private DataManager dataManager;

    public FileDescriptor zipFiles(Folder parentFolder,
                                   List<FileDescriptor> files,
                                   String zipFileName,
                                   String zipPassword) throws IOException {

        if (parentFolder == null) {
            throw new IllegalArgumentException("parentFolder không được null");
        }

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Danh sách file cần nén đang rỗng.");
        }

        if (zipFileName == null || zipFileName.isBlank()) {
            zipFileName = "selected-files.zip";
        } else if (!zipFileName.toLowerCase().endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        SourceStorage sourceStorage = parentFolder.getSourceStorage();
        if (sourceStorage == null) {
            throw new IllegalStateException("Folder không có SourceStorage.");
        }

        List<ZipEntrySourceDto> zipEntrySources = new ArrayList<>();

        for (FileDescriptor file : files) {
            if (file.getFileRef() == null) {
                continue;
            }

            // Nếu cần, có thể check tất cả file cùng sourceStorage
            // if (!sourceStorage.equals(file.getSourceStorage())) { ... }

            String zipEntryPath = file.getName(); // tên file trong ZIP

            ZipEntrySourceDto dto = new ZipEntrySourceDto(
                    file,
                    zipEntryPath,
                    sourceStorage
            );
            zipEntrySources.add(dto);
        }

        if (zipEntrySources.isEmpty()) {
            throw new IllegalStateException("Không có file hợp lệ để nén.");
        }

        FileRef zipFileRef = zipCompressionService.zipEntries(
                zipEntrySources,
                zipFileName,
                zipPassword
        );

        FileDescriptor zipDescriptor = dataManager.create(FileDescriptor.class);
        zipDescriptor.setName(zipFileName);
        zipDescriptor.setFileRef(zipFileRef);
        zipDescriptor.setFolder(parentFolder);
        zipDescriptor.setExtension("zip");
        zipDescriptor.setSourceStorage(sourceStorage);
        zipDescriptor.setInTrash(false);
        zipDescriptor.setLastModified(LocalDateTime.now());

        return dataManager.save(zipDescriptor);
    }
}
