package com.vn.ecm.service.ecm.zipfile;

import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ZipFolderService {
    private final DataManager dataManager;
    @Autowired
    private  FolderTraversalService folderTraversalService;
    @Autowired
    private  ZipCompressionService zipCompressionService;

    public ZipFolderService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * Nén toàn bộ nội dung của một Folder (bao gồm tất cả folder con)
     */
    public FileDescriptor zipFolder(Folder rootFolder,
                                    String zipFileName,
                                    String zipPassword) throws IOException {

        if (rootFolder == null) {
            throw new IllegalArgumentException("rootFolder không được null");
        }

        if (zipFileName == null || zipFileName.isBlank()) {
            zipFileName = rootFolder.getName() + ".zip";
        } else if (!zipFileName.toLowerCase().endsWith(".zip")) {
            zipFileName += ".zip";
        }

        List<ZipEntrySourceDto> zipEntrySources =
                folderTraversalService.buildPathRecursively(rootFolder);

        FileRef zipFileRef = zipCompressionService.zipEntries(
                zipEntrySources,
                zipFileName,
                zipPassword
        );

        FileDescriptor zipDescriptor = dataManager.create(FileDescriptor.class);
        zipDescriptor.setName(zipFileName);
        zipDescriptor.setFileRef(zipFileRef);
        zipDescriptor.setFolder(rootFolder);

        SourceStorage sourceStorage = rootFolder.getSourceStorage();
        zipDescriptor.setSourceStorage(sourceStorage);
        zipDescriptor.setExtension("zip");

        zipDescriptor.setInTrash(false);
        zipDescriptor.setLastModified(LocalDateTime.now());

        return dataManager.save(zipDescriptor);
    }
}
