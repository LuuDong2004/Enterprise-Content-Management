package com.vn.ecm.service.ecm.folderandfile.Impl;
//v1
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.SourceStorage;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorService;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileDescriptorService implements IFileDescriptorService {

    @Autowired
    private DataManager dataManager;

    @Override
    public void removeFileToTrash(FileDescriptor fileDescriptor,String user) {
        if(fileDescriptor == null ){
            return;
        }
        fileDescriptor.setInTrash(true);
        fileDescriptor.setDeleteDate(java.time.LocalDateTime.now());
        fileDescriptor.setDeletedBy(user);
        dataManager.save(fileDescriptor);
    }
    /**
     * Tránh trùng tên trong cùng folder + sourceStorage.
     * Bỏ qua chính file đang rename.
     */
    @Override
    public FileDescriptor renameFile(FileDescriptor file, String newName, String user) {
        if (file == null || newName == null || newName.isBlank()) return file;

//        Folder folder = file.getFolder();
//        SourceStorage src = file.getSourceStorage();
//        UUID selfId = file.getId();

        file.setName(newName);
        // cập nhật extension
        int dot = newName.lastIndexOf('.');
        file.setExtension((dot > 0 && dot < newName.length() - 1) ? newName.substring(dot + 1) : null);

        file.setLastModified(LocalDateTime.now());
        file.setCreateBy(user);
        return dataManager.save(file);
    }


    @Override
    public FileDescriptor findByName(Folder folder, SourceStorage src, String name) {
        return dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f " +
                        "where f.folder = :folder and f.sourceStorage = :src " +
                        "and f.inTrash = false and lower(f.name) = lower(:name)")
                .parameter("folder", folder)
                .parameter("src", src)
                .parameter("name", name)
                .optional()
                .orElse(null);
    }


    private boolean existsName(Folder folder, SourceStorage src, String name, UUID excludeId) {
        Long cnt = dataManager.loadValue(
                        "select count(f) from FileDescriptor f " +
                                "where f.folder = :folder and f.sourceStorage = :src " +
                                "and f.inTrash = false and f.name = :name " +
                                "and (:id is null or f.id <> :id)",
                        Long.class)
                .parameter("folder", folder)
                .parameter("src", src)
                .parameter("name", name)
                .parameter("id", excludeId)
                .one();
        return cnt != null && cnt > 0L;
    }
    /**
     * Trả về tên không trùng trong Folder + SourceStorage (bỏ qua chính file có id = excludeId).
     * Dùng count(*) để kiểm tra tồn tại, tránh load list.
     */
    private String ensureUniqueName(Folder folder, SourceStorage src, String proposed, UUID excludeId) {
        if (!existsName(folder, src, proposed, excludeId)) return proposed;

        String[] parts = splitBaseExt(proposed); // [0]=base, [1]=ext (có thể null)
        String base = parts[0];
        String ext = parts[1];

        int n = 1;
        while (true) {
            String candidate = ext == null ? base + " (" + n + ")" : base + " (" + n + ")." + ext;
            if (!existsName(folder, src, candidate, excludeId)) return candidate;
            n++;
        }
    }
    /**
     * Tách tên thành base + ext. Ví dụ: "report.pdf" -> ["report","pdf"]; "README" -> ["README", null]
     */
    private String[] splitBaseExt(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return new String[]{name, null};
        return new String[]{name.substring(0, dot), name.substring(dot + 1)};
    }

    public String suggestUniqueName(Folder folder, SourceStorage src, String proposed) {
        // excludeId = null vì là file mới
        return ensureUniqueName(folder, src, proposed, null);
    }


}
