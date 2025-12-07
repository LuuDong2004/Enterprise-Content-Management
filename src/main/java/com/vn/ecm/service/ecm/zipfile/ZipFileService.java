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

        if (files.isEmpty()) {
            throw new IllegalArgumentException("Danh sách file cần nén đang rỗng.");
        }

        // Chuẩn hóa tên file ZIP
        String normalizedZipName = (zipFileName == null || zipFileName.isBlank())
                ? "archive.zip"
                : zipFileName.trim();

        if (!normalizedZipName.toLowerCase().endsWith(".zip")) {
            normalizedZipName = normalizedZipName + ".zip";
        }

        SourceStorage sourceStorage = parentFolder.getSourceStorage();
        if (sourceStorage == null) {
            throw new IllegalStateException("Folder không có SourceStorage.");
        }

        List<ZipEntrySourceDto> zipEntrySources = new ArrayList<>();
        for (FileDescriptor file : files) {
            if (file == null || file.getFileRef() == null) {
                continue;
            }
            String zipEntryPath = file.getName();

            zipEntrySources.add(new ZipEntrySourceDto(
                    file,
                    zipEntryPath,
                    sourceStorage
            ));
        }

        if (zipEntrySources.isEmpty()) {
            throw new IllegalStateException("Không có file hợp lệ để nén.");
        }

        FileRef zipFileRef = zipCompressionService.zipEntries(
                zipEntrySources,
                normalizedZipName,
                zipPassword
        );
        FileDescriptor zipDescriptor = dataManager.create(FileDescriptor.class);
        zipDescriptor.setName(normalizedZipName);
        zipDescriptor.setFileRef(zipFileRef);
        zipDescriptor.setFolder(parentFolder);
        zipDescriptor.setExtension("zip");
        zipDescriptor.setSourceStorage(sourceStorage);
        zipDescriptor.setInTrash(false);
        zipDescriptor.setLastModified(LocalDateTime.now());

        return dataManager.save(zipDescriptor);
    }
}
