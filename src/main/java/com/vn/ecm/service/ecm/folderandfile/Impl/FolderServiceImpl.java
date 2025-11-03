package com.vn.ecm.service.ecm.folderandfile.Impl;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String desiredName = folder.getName().trim();

        String uniqueName = generateUniqueName(
                folder.getParent(),
                folder.getSourceStorage(),
                desiredName
        );
        Folder f = dataManager.create(Folder.class);
        f.setId(UUID.randomUUID());
        f.setName(uniqueName);
        f.setParent(folder.getParent());
        f.setSourceStorage(folder.getSourceStorage());
        f.setCreatedDate(LocalDateTime.now());
        f.setFullPath(buildFolderPath(folder));
        f.setInTrash(false);
        return dataManager.save(f);
    }
    private String generateUniqueName(Folder parent, Object sourceStorage, String desiredName) {
        String baseName = stripIndexSuffix(desiredName);
        int counter = 0;

        while (isNameExists(parent, sourceStorage, buildIndexedName(baseName, counter))) {
            counter++;
        }
        return buildIndexedName(baseName, counter);
    }

    /** Kiểm tra trùng tên folder (không phân biệt hoa thường, không tính folder trong trash) */
    private boolean isNameExists(Folder parent, Object sourceStorage, String name) {
        Long count = dataManager.loadValue(
                        "select count(f) from Folder f " +
                                "where f.parent = :parent " +
                                "and f.sourceStorage = :storage " +
                                "and f.inTrash = false " +
                                "and lower(f.name) = lower(:name)",
                        Long.class
                )
                .parameter("parent", parent)
                .parameter("storage", sourceStorage)
                .parameter("name", name)
                .one();
        return count != null && count > 0;
    }

    /** Xóa đuôi "(n)" nếu người dùng nhập tên như "Folder (3)" */
    private String stripIndexSuffix(String name) {
        Pattern pattern = Pattern.compile("^(.+?)\\s*\\((\\d+)\\)$");
        Matcher matcher = pattern.matcher(name);
        return matcher.matches() ? matcher.group(1).trim() : name;
    }

    /** Ghép tên với chỉ số */
    private String buildIndexedName(String base, int index) {
        return index == 0 ? base : base + " (" + index + ")";
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
            return ;
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
