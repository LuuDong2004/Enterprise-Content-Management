package com.vn.ecm.service.ecm.zipfile;

import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.DataManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FolderTraversalService {
    private final DataManager dataManager;

    public FolderTraversalService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<ZipEntrySourceDto> buildZipEntriesForFolder(Folder rootFolder) {
        List<ZipEntrySourceDto> zipEntries = new ArrayList<>();
        traverseFolder(rootFolder, "", zipEntries);
        return zipEntries;
    }
    /**
     * Hàm đệ quy duyệt folder.
     */
    private void traverseFolder(Folder folder,
                                String path,
                                List<ZipEntrySourceDto> zipEntrySourceDtos) {

        String currentZipPath = (path == null || path.isEmpty())
                ? folder.getName()
                : path + "/" + folder.getName();

        // 1. Lấy file trực tiếp trong folder hiện tại
        List<FileDescriptor> files = dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f " +
                        "where f.folder = :folder and f.inTrash = false")
                .parameter("folder", folder)
                .list();

        for (FileDescriptor fileDescriptor : files) {
            // Bỏ qua file chưa upload storage
            if (fileDescriptor.getFileRef() == null) {
                continue;
            }

            String zipEntryPath = currentZipPath + "/" + fileDescriptor.getName();
            ZipEntrySourceDto dto = new ZipEntrySourceDto(fileDescriptor, zipEntryPath);
            zipEntrySourceDtos.add(dto);
        }

        // 2. Duyệt các folder con
        List<Folder> childFolders = dataManager.load(Folder.class)
                .query("select f from Folder f " +
                        "where f.parent = :parent and f.inTrash = false")
                .parameter("parent", folder)
                .list();

        for (Folder child : childFolders) {
            traverseFolder(child, currentZipPath, zipEntrySourceDtos);
        }
    }
}
