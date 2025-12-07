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

    public List<ZipEntrySourceDto> buildPathRecursively(Folder folder) {
        List<ZipEntrySourceDto> zipEntrySourceDtoList = new ArrayList<>();
        traverseFolder(folder, "", zipEntrySourceDtoList , folder.getSourceStorage());
        return zipEntrySourceDtoList;
    }

    /**
     * Hàm đệ quy duyệt folder.
     *
     * @param folder                   folder hiện tại
     * @param parentZipPath            đường dẫn folder cha bên trong ZIP
     * @param collectedZipEntrySources danh sách kết quả
     */
    private void traverseFolder(Folder folder,
                                String parentZipPath,
                                List<ZipEntrySourceDto> collectedZipEntrySources,
                                SourceStorage sourceStorage) {

        String currentZipPath = (parentZipPath == null || parentZipPath.isEmpty())
                ? folder.getName()
                : parentZipPath + "/" + folder.getName();

        List<FileDescriptor> fileDescriptors = dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f where f.folder = :folder")
                .parameter("folder", folder)
                .list();

        for (FileDescriptor fileDescriptor : fileDescriptors) {
            String zipEntryPath = currentZipPath + "/" + fileDescriptor.getName();
            ZipEntrySourceDto dto =
                    new ZipEntrySourceDto(fileDescriptor, zipEntryPath, sourceStorage);
            collectedZipEntrySources.add(dto);
        }

        List<Folder> childFolders = dataManager.load(Folder.class)
                .query("select f from Folder f where f.parent = :folder")
                .parameter("folder", folder)
                .list();

        for (Folder childFolder : childFolders) {
            traverseFolder(childFolder, currentZipPath, collectedZipEntrySources, sourceStorage);
        }
    }
}
