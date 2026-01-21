package com.vn.ecm.service.ecm.zipfile;

import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.Folder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZipFolderService {

    private final FolderTraversalService folderTraversalService;
    @Autowired
    private  ZipCompressionService zipCompressionService;

    public ZipFolderService( FolderTraversalService folderTraversalService) {
        this.folderTraversalService = folderTraversalService;
    }

    /**
     * Nén toàn bộ folder (kèm folder con) thành ZIP, trả về byte[].
     * Không tạo FileDescriptor cho file ZIP, chỉ dùng để download trực tiếp.
     */
    public byte[] zipFolder(Folder rootFolder,
                            String fileName,
                            String password) throws Exception {

        if (rootFolder == null) {
            throw new IllegalArgumentException("Folder không được null");
        }

        // Chuẩn hoá tên file ZIP
        if (fileName == null || fileName.isBlank()) {
            fileName = (rootFolder.getName() != null && !rootFolder.getName().isBlank())
                    ? rootFolder.getName() + ".zip"
                    : "folder.zip";
        } else if (!fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName + ".zip";
        }

        List<ZipEntrySourceDto> entries = folderTraversalService.buildZipEntriesForFolder(rootFolder);

        if (entries.isEmpty()) {
            throw new IllegalStateException("Thư mục không có file hợp lệ để nén.");
        }

        return zipCompressionService.zipEntries(entries, fileName, password);
    }

}
