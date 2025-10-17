package com.vn.ecm.service.ecm.Impl;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.ecm.IFolderService;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FolderServiceImpl implements IFolderService {
    @Autowired
    private final Messages messages;
    @Autowired
    private DataManager dataManager;

    public FolderServiceImpl(Messages messages) {
        this.messages = messages;
    }


    // tạo mới folder
    @Override
    public Folder createFolder(Folder folder) {
        Folder f = dataManager.create(Folder.class);
        f.setId(UUID.randomUUID());
        f.setName(folder.getName());
        f.setParent(folder.getParent());
        f.setSourceStorage(folder.getSourceStorage());
        f.setCreatedDate(LocalDateTime.now());
        f.setFullPath(buildFolderPath(folder));
        f.setInTrash(false);
        return dataManager.save(f);
    }


    //xóa cứng
    @Override
    public boolean deleteFolderFromTrash(Folder folder){
        if(folder == null)  return false;

        List<FileDescriptor> files = dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f where f.folder = :folder")
                .parameter("folder", folder)
                .list();

        // Kiểm tra có folder con
        List<Folder> subFolders = dataManager.load(Folder.class)
                .query("select f from Folder f where f.parent = :parent")
                .parameter("parent", folder)
                .list();

        if (!files.isEmpty() || !subFolders.isEmpty()) {
            messages.getMessage(getClass(), "ecmDeleteFolderAlert");
            return false;
        }
        dataManager.remove(folder);
        return true;
    }


    // xử lý logic xóa folder - đệ quy khỏi thùng rác
    @Override
    public int deleteFolderRecursivelyFromTrash(Folder folder) {
        if (folder == null) return 0;

        int deletedCount = 0;

        // Xóa file trong folder
        List<FileDescriptor> files = dataManager.load(FileDescriptor.class)
                .query("select f from FileDescriptor f where f.folder = :folder")
                .parameter("folder", folder)
                .list();

        for (FileDescriptor file : files) {
            // thiếu xử lý logic xóa kho ....
            dataManager.remove(file);
            deletedCount++;
        }

        // Xóa các subfolder
        List<Folder> subFolders = dataManager.load(Folder.class)
                .query("select f from Folder f where f.parent = :parent")
                .parameter("parent", folder)
                .list();

        for (Folder sub : subFolders) {
            deletedCount += deleteFolderRecursivelyFromTrash(sub);
        }

        // Cuối cùng xóa chính folder này
        dataManager.remove(folder);
        return deletedCount + 1;
    }


    //xóa vào thùng rác
    @Override
    public void moveToTrash(Folder folder, String username) {
        if (folder == null)
            return;
        folder.setInTrash(true);
        folder.setDeleteDate(LocalDateTime.now());
        folder.setDeletedBy(username);
        dataManager.save(folder);
    }

    //  khôi phục từ thùng rác
    @Override
    public Folder restoreFromTrash(Folder folder) {
        if (folder == null) return null;
        folder.setInTrash(false);
        folder.setDeletedBy(null);
        folder.setDeletedBy(null);
        return dataManager.save(folder);
    }


    // đổi tên folder
    @Override
    public Folder renameFolder(Folder folder , String newName){
        if(folder == null || newName == null || newName.isBlank()){
            return null;
        }

        folder.setName(newName);
        folder.setFullPath(buildFolderPath(folder));
        folder.setCreatedDate(LocalDateTime.now());


        Folder saved = dataManager.save(folder);

        // Cập nhật fullPath cho toàn bộ folder con (nếu có)
        updateChildFullPaths(saved);

        return saved;
    }


    // cập nhập full path đệ quy của các folder con
    private void updateChildFullPaths(Folder parentFolder) {
        List<Folder> children = dataManager.load(Folder.class)
                .query("select f from Folder f where f.parent = :parent")
                .parameter("parent", parentFolder)
                .list();

        for (Folder child : children) {
            child.setFullPath(buildFolderPath(child));
            dataManager.save(child);
            updateChildFullPaths(child); // đệ quy tiếp
        }
    }
    @Override
    public String buildFolderPath(Folder folder){
        StringBuilder path = new StringBuilder();
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        return path.toString();
    }
}
