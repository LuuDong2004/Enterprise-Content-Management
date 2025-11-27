package com.vn.ecm.service.ecm.folderandfile.Impl;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.security.CurrentAuthentication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FolderServiceImpl implements IFolderService {
    @Autowired
    private final Messages messages;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @PersistenceContext
    private EntityManager entityManager;

    public FolderServiceImpl(Messages messages) {
        this.messages = messages;
    }

    // tạo mới folder
    @Override
    public Folder createFolder(Folder folder) {
        String name = folder.getName().trim();
        Folder f = dataManager.create(Folder.class);
        f.setId(UUID.randomUUID());
        f.setName(name);
        f.setParent(folder.getParent());
        f.setSourceStorage(folder.getSourceStorage());
        f.setCreatedDate(LocalDateTime.now());
        f.setFullPath(buildFolderPath(f));
        f.setInTrash(false);
        Folder f2 = dataManager.save(f);
        User user = (User) currentAuthentication.getUser();
        permissionService.initializeFolderPermission(user, f2);
        return f2;
    }

    // xóa cứng
    @Override
    public boolean deleteFolderFromTrash(Folder folder) {
        if (folder == null)
            return false;
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
        if (folder == null)
            return 0;

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

    // xóa vào thùng rác
    @Override
    public void moveToTrash(Folder folder, String username) {
        if (folder == null)
            return;
        folder.setInTrash(true);
        folder.setDeleteDate(LocalDateTime.now());
        folder.setDeletedBy(username);
        dataManager.save(folder);
    }

    // khôi phục từ thùng rác
    @Override
    public Folder restoreFromTrash(Folder folder) {
        if (folder == null)
            return null;
        folder.setInTrash(false);
        folder.setDeletedBy(null);
        folder.setDeletedBy(null);
        return dataManager.save(folder);
    }

    // đổi tên folder
    @Override
    public Folder renameFolder(Folder folder, String newName) {
        if (folder == null || newName == null || newName.isBlank()) {
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
    public String buildFolderPath(Folder folder) {
        StringBuilder path = new StringBuilder();
        Folder parent = folder.getParent();
        while (parent != null) {
            path.insert(0, parent.getName() + "/");
            parent = parent.getParent();
        }
        // Cuối cùng thêm tên chính folder
        path.append(folder.getName()).append("/");
        return path.toString();
    }

    @Override
    public Folder findExistingFolder(Folder parent, Object sourceStorage, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return dataManager.load(Folder.class)
                    .query("select f from Folder f " +
                            "where ( ( :parent is null and f.parent is null ) or f.parent = :parent ) " +
                            "and f.sourceStorage = :storage " +
                            "and f.inTrash = false " +
                            "and lower(f.name) = lower(:name)")
                    .parameter("parent", parent)
                    .parameter("storage", sourceStorage)
                    .parameter("name", name.trim().toLowerCase())
                    .one();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional
    public Folder moveFolder(Folder source, Folder target) {
        if (source == null || target == null) {
            return null;
        }
        if (source.getId() == null || target.getId() == null) {
            return null;
        }
        // Không cho phép move vào chính nó
        if (source.getId().equals(target.getId())) {
            return source;
        }
        // Không cho phép move khác kho lưu trữ
        if (source.getSourceStorage() != null && target.getSourceStorage() != null
                && !source.getSourceStorage().getId().equals(target.getSourceStorage().getId())) {
            return source;
        }

        entityManager
                .createNativeQuery("EXEC dbo.usp_MoveFolderWithFullPath ?1, ?2")
                .setParameter(1, source.getId())
                .setParameter(2, target.getId())
                .executeUpdate();

        // Reload lại folder sau khi DB đã cập nhật
        return dataManager.load(Folder.class)
                .id(source.getId())
                .optional()
                .orElse(source);
    }

    @Override
    @Transactional
    public Folder moveFolderPathOnly(Folder source, Folder target) {
        if (source == null || target == null) {
            return null;
        }
        if (source.getId() == null || target.getId() == null) {
            return null;
        }
        // Không cho phép move vào chính nó
        if (source.getId().equals(target.getId())) {
            return source;
        }
        // Không cho phép move khác kho lưu trữ
        if (source.getSourceStorage() != null && target.getSourceStorage() != null
                && !source.getSourceStorage().getId().equals(target.getSourceStorage().getId())) {
            return source;
        }

        // Lấy FULL_PATH của source và target
        Folder sourceLoaded = dataManager.load(Folder.class).id(source.getId()).one();
        Folder targetLoaded = dataManager.load(Folder.class).id(target.getId()).one();

        if (sourceLoaded.getFullPath() == null || targetLoaded.getFullPath() == null) {
            return source;
        }
        String oldPrefix = sourceLoaded.getFullPath();
        String newPrefix = targetLoaded.getFullPath() + sourceLoaded.getName() + "/";
        entityManager
                .createNativeQuery(
                        "UPDATE F " +
                                "SET FULL_PATH = STUFF(F.FULL_PATH, 1, LEN(?1), ?2) " +
                                "FROM FOLDER F " +
                                "WHERE F.FULL_PATH LIKE ?3")
                .setParameter(1, oldPrefix)
                .setParameter(2, newPrefix)
                .setParameter(3, oldPrefix + "%")
                .executeUpdate();

        // Cập nhật parent_ID của source
        entityManager
                .createNativeQuery("UPDATE FOLDER SET parent_ID = ?1 WHERE ID = ?2")
                .setParameter(1, target.getId())
                .setParameter(2, source.getId())
                .executeUpdate();
        // Reload lại folder sau khi DB đã cập nhật
        return dataManager.load(Folder.class)
                .id(source.getId())
                .optional()
                .orElse(source);
    }

}
