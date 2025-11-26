package com.vn.ecm.service.ecm;

import com.vn.ecm.entity.*;

import io.jmix.core.DataManager;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PermissionService {

    private final DataManager dataManager;

    private final JdbcTemplate jdbcTemplate;

    public PermissionService(DataManager dataManager, JdbcTemplate jdbcTemplate) {
        this.dataManager = dataManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recalcAccessForUser(UUID userId) {
        jdbcTemplate.update("EXEC dbo.usp_RecalculateAccessForUser @UserId = ?", userId);
    }

    public Map<String, Object> getEffectiveForUserFolder(UUID userId, UUID folderId) {
        return jdbcTemplate.queryForMap("EXEC dbo.usp_GetEffectivePermissionForUserFolder @UserId = ?, @FolderId = ?",
                userId, folderId);
    }

    public int getEffectiveMask(UUID userId, UUID folderId) {
        Integer mask = dataManager.loadValue(
                "select calculate_effective_permission(:userId, :folderId)",
                Integer.class)
                .parameter("userId", userId)
                .parameter("folderId", folderId)
                .one();

        return mask != null ? mask : 0;
    }

    public void updateFolderClosureForMove(UUID folderId) {
        if (folderId == null) {
            return;
        }
        jdbcTemplate.update("EXEC dbo.usp_UpdateFolderClosureForMove @FolderId = ?", folderId);
    }

    @Transactional
    // SAVE permission (User)
    public void savePermission(Collection<Permission> permissions, User user, Folder folder) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(user, folder);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setUser(user);
            permission.setFolder(folder);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);

        // propagate to children if folder
        propagateToChildren(user, null, folder, mask);
    }

    @Transactional
    public void savePermission(Collection<Permission> permissions, User user, FileDescriptor FileDescriptor) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(user, FileDescriptor);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setUser(user);
            permission.setFile(FileDescriptor);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);
    }

    @Transactional
    // SAVE permission (Role)
    public void savePermission(Collection<Permission> permissions, ResourceRoleEntity role, Folder folder) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(role, folder);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setRoleCode(role.getCode());
            permission.setFolder(folder);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);

        propagateToChildren(null, role, folder, mask);
    }

    @Transactional
    public void savePermission(Collection<Permission> permissions, ResourceRoleEntity role,
            FileDescriptor FileDescriptor) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(role, FileDescriptor);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setRoleCode(role.getCode());
            permission.setFile(FileDescriptor);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);
    }

    @Transactional
    // LOAD permission
    public Permission loadPermission(User user, Folder folder) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.folder = :folder")
                .parameter("user", user)
                .parameter("folder", folder)
                .optional()
                .orElse(null);
    }

    @Transactional
    public Permission loadPermission(User user, FileDescriptor FileDescriptor) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :FileDescriptor")
                .parameter("user", user)
                .parameter("FileDescriptor", FileDescriptor)
                .optional()
                .orElse(null);
    }

    @Transactional
    public Permission loadPermission(ResourceRoleEntity role, Folder folder) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.folder = :folder")
                .parameter("roleCode", role.getCode())
                .parameter("folder", folder)
                .optional()
                .orElse(null);
    }

    @Transactional
    public Permission loadPermission(ResourceRoleEntity role, FileDescriptor FileDescriptor) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :FileDescriptor")
                .parameter("roleCode", role.getCode())
                .parameter("FileDescriptor", FileDescriptor)
                .optional()
                .orElse(null);
    }

    @Transactional
    // HAS permission
    public boolean hasPermission(User user, PermissionType type, FileDescriptor FileDescriptor) {
        Permission p = loadPermission(user, FileDescriptor);
        if (p != null && PermissionType.hasPermission(Optional.ofNullable(p.getPermissionMask()).orElse(0), type)) {
            return true;
        }
        Folder folder = FileDescriptor.getFolder();
        return folder != null && hasPermission(user, type, folder);
    }

    @Transactional
    public boolean hasPermission(User user, PermissionType type, Folder folder) {
        Folder cur = folder;
        while (cur != null) {
            Permission p = loadPermission(user, cur);
            if (p != null) {
                int mask = Optional.ofNullable(p.getPermissionMask()).orElse(0);
                return PermissionType.hasPermission(mask, type);
            }
            cur = cur.getParent();
        }
        return false;
    }

    // normalize & mask
    private void normalizePermissions(Collection<Permission> permissions) {
        boolean readDenied = permissions.stream()
                .anyMatch(p -> p.getPermissionType() == PermissionType.READ && Boolean.FALSE.equals(p.getAllow()));

        if (readDenied) {
            for (Permission p : permissions) {
                if (p.getPermissionType() == PermissionType.CREATE
                        || p.getPermissionType() == PermissionType.MODIFY) {
                    p.setAllow(false);
                }
            }
        }

        boolean hasFull = permissions.stream()
                .anyMatch(p -> p.getPermissionType() == PermissionType.FULL && Boolean.TRUE.equals(p.getAllow()));

        if (hasFull) {
            for (Permission p : permissions) {
                if (p.getPermissionType() != PermissionType.FULL) {
                    p.setAllow(false);
                }
            }
        }
    }

    private int buildMask(Collection<Permission> permissions) {
        int mask = 0;
        for (Permission p : permissions) {
            if (Boolean.TRUE.equals(p.getAllow())) {
                if (p.getPermissionType() == PermissionType.FULL) {
                    return PermissionType.FULL.getValue();
                }
                mask |= p.getPermissionType().getValue();
            }
        }
        return mask;
    }

    // build full path
    public String getFullPath(Folder folder) {
        if (folder == null)
            return "";
        List<String> parts = new ArrayList<>();
        Folder cur = folder;
        while (cur != null) {
            String name = cur.getName();
            if (name == null || name.isBlank()) {
                // fallback to id if no name
                name = cur.getId() != null ? cur.getId().toString() : "";
            }
            parts.add(name);
            cur = cur.getParent();
        }
        Collections.reverse(parts);
        return String.join("/", parts);
    }

    public String getFullPath(FileDescriptor FileDescriptor) {
        if (FileDescriptor == null)
            return "";
        String folderPath = getFullPath(FileDescriptor.getFolder());
        String FileDescriptorName = FileDescriptor.getName() != null ? FileDescriptor.getName()
                : (FileDescriptor.getId() != null ? FileDescriptor.getId().toString() : "");
        if (folderPath.isEmpty())
            return FileDescriptorName;
        return folderPath + "/" + FileDescriptorName;
    }

    /**
     * Return a human-friendly identifier for an ancestor permission.
     * Previously returned "FOLDER:<id>" — now returns full path string (no prefix).
     */
    private String ancestorIdentifier(Permission anc) {
        if (anc == null)
            return null;
        if (anc.getFolder() != null) {
            return getFullPath(anc.getFolder());
        }
        if (anc.getFile() != null) {
            return getFullPath(anc.getFile());
        }
        return null;
    }

    // Propagate to children
    @Transactional
    protected void propagateToChildren(User user, ResourceRoleEntity role, Folder parentFolder, int parentMask) {
        if (parentFolder == null) {
            return;
        }
        UUID userId = user != null ? user.getId() : null;
        String roleCode = role != null ? role.getCode() : null;
        if (userId == null && (roleCode == null || roleCode.isBlank())) {
            return;
        }
        jdbcTemplate.update(
                "EXEC dbo.usp_PermissionPropagateFromFolder @FolderId = ?, @ParentMask = ?, @UserId = ?, @RoleCode = ?",
                parentFolder.getId(),
                parentMask,
                userId,
                roleCode);
    }

    // Fixed disableInheritance methods for User + Folder
    @Transactional
    public void disableInheritance(User user, Folder folder, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.folder = :folder")
                .parameter("user", user)
                .parameter("folder", folder)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);
            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    // Convert to explicit permission
                    perm.setInherited(false);
                    perm.setInheritedFrom(null);
                    dataManager.save(perm);

                    // CRITICAL FIX: Propagate this explicit permission to children
                    // so they inherit from THIS node, not from ancestor
                    int explicitMask = Optional.ofNullable(perm.getPermissionMask()).orElse(0);
                    propagateToChildren(user, null, folder, explicitMask);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    // Fixed disableInheritance for User + FileDescriptor
    @Transactional
    public void disableInheritance(User user, FileDescriptor fileDescriptor, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :FileDescriptor")
                .parameter("user", user)
                .parameter("FileDescriptor", fileDescriptor)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);
            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    // Convert to explicit permission
                    perm.setInherited(false);
                    perm.setInheritedFrom(null);
                    dataManager.save(perm);
                    // Files don't have children, no propagation needed
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    // Fixed disableInheritance for Role + Folder
    @Transactional
    public void disableInheritance(ResourceRoleEntity role, Folder folder, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.folder = :folder")
                .parameter("roleCode", role.getCode())
                .parameter("folder", folder)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);
            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    // Convert to explicit permission
                    perm.setInherited(false);
                    perm.setInheritedFrom(null);
                    dataManager.save(perm);

                    // CRITICAL FIX: Propagate this explicit permission to children
                    int explicitMask = Optional.ofNullable(perm.getPermissionMask()).orElse(0);
                    propagateToChildren(null, role, folder, explicitMask);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    // Fixed disableInheritance for Role + FileDescriptor
    @Transactional
    public void disableInheritance(ResourceRoleEntity role, FileDescriptor fileDescriptor, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :FileDescriptor")
                .parameter("roleCode", role.getCode())
                .parameter("FileDescriptor", fileDescriptor)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);
            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    // Convert to explicit permission
                    perm.setInherited(false);
                    perm.setInheritedFrom(null);
                    dataManager.save(perm);
                    // Files don't have children, no propagation needed
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    // Enable inheritance
    @Transactional
    public void enableInheritance(User user, Folder folder) {
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.folder = :folder")
                .parameter("user", user)
                .parameter("folder", folder)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }
        Permission ancestor = findNearestAncestorPermission(user, folder.getParent());
        if (ancestor != null) {
            Permission nodePerm = loadPermission(user, folder);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setUser(user);
                nodePerm.setFolder(folder);
                nodePerm.setPermissionMask(ancestor.getPermissionMask());
                nodePerm.setInherited(true);
                nodePerm.setInheritEnabled(true);
                nodePerm.setInheritedFrom(ancestorIdentifier(ancestor));
                nodePerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
                dataManager.save(nodePerm);
            }
            propagateToChildren(user, null, folder, ancestor.getPermissionMask());
        }
    }

    @Transactional
    public void enableInheritance(User user, FileDescriptor FileDescriptor) {
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :FileDescriptor")
                .parameter("user", user)
                .parameter("FileDescriptor", FileDescriptor)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }
        Folder start = FileDescriptor.getFolder();
        Permission ancestor = findNearestAncestorPermission(user, start);
        if (ancestor != null) {
            Permission nodePerm = loadPermission(user, FileDescriptor);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setUser(user);
                nodePerm.setFile(FileDescriptor);
                nodePerm.setPermissionMask(ancestor.getPermissionMask());
                nodePerm.setInherited(true);
                nodePerm.setInheritEnabled(true);
                nodePerm.setInheritedFrom(ancestorIdentifier(ancestor));
                nodePerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                dataManager.save(nodePerm);
            }
        }
    }

    @Transactional
    public void enableInheritance(ResourceRoleEntity role, Folder folder) {
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.folder = :folder")
                .parameter("roleCode", role.getCode())
                .parameter("folder", folder)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }
        Permission ancestor = findNearestAncestorPermission(role, folder.getParent());
        if (ancestor != null) {
            Permission nodePerm = loadPermission(role, folder);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setRoleCode(role.getCode());
                nodePerm.setFolder(folder);
                nodePerm.setPermissionMask(ancestor.getPermissionMask());
                nodePerm.setInherited(true);
                nodePerm.setInheritEnabled(true);
                nodePerm.setInheritedFrom(ancestorIdentifier(ancestor));
                nodePerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
                dataManager.save(nodePerm);
            }
            propagateToChildren(null, role, folder, ancestor.getPermissionMask());
        }
    }

    @Transactional
    public void enableInheritance(ResourceRoleEntity role, FileDescriptor FileDescriptor) {
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :FileDescriptor")
                .parameter("roleCode", role.getCode())
                .parameter("FileDescriptor", FileDescriptor)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }
        Folder start = FileDescriptor.getFolder();
        Permission ancestor = findNearestAncestorPermission(role, start);
        if (ancestor != null) {
            Permission nodePerm = loadPermission(role, FileDescriptor);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setRoleCode(role.getCode());
                nodePerm.setFile(FileDescriptor);
                nodePerm.setPermissionMask(ancestor.getPermissionMask());
                nodePerm.setInherited(true);
                nodePerm.setInheritEnabled(true);
                nodePerm.setInheritedFrom(ancestorIdentifier(ancestor));
                nodePerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                dataManager.save(nodePerm);
            }
        }
    }

    // Enable remove inheritance
    @Transactional
    public void enableRemoveInheritance(Folder targetFolder) {
        if (targetFolder == null)
            return;
        Folder current = targetFolder.getParent();
        while (current != null) {
            List<Permission> ancestorPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.folder = :folder")
                    .parameter("folder", current)
                    .list();

            if (!ancestorPerms.isEmpty()) {
                for (Permission anc : ancestorPerms) {
                    Permission existing = dataManager.load(Permission.class)
                            .query("select p from Permission p where " +
                                    "(p.user = :user and p.folder = :target) or (p.roleCode = :roleCode and p.folder = :target)")
                            .parameter("user", anc.getUser())
                            .parameter("roleCode", anc.getRoleCode())
                            .parameter("target", targetFolder)
                            .optional()
                            .orElse(null);

                    if (existing != null) {
                        existing.setInheritEnabled(true);
                        dataManager.save(existing);
                    } else {
                        Permission inher = dataManager.create(Permission.class);
                        if (anc.getUser() != null)
                            inher.setUser(anc.getUser());
                        else
                            inher.setRoleCode(anc.getRoleCode());
                        inher.setFolder(targetFolder);
                        inher.setPermissionMask(anc.getPermissionMask());
                        inher.setInherited(true);
                        inher.setInheritEnabled(true);
                        inher.setInheritedFrom(ancestorIdentifier(anc)); // full path
                        inher.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
                        dataManager.save(inher);
                    }
                    // propagate for each principal
                    if (anc.getUser() != null)
                        propagateToChildren(anc.getUser(), null, targetFolder, anc.getPermissionMask());
                    else {
                        ResourceRoleEntity roleEntity = loadRoleByCode(anc.getRoleCode());
                        if (roleEntity != null)
                            propagateToChildren(null, roleEntity, targetFolder, anc.getPermissionMask());
                    }
                }
                return;
            }
            current = current.getParent();
        }
    }

    @Transactional
    public void enableRemoveInheritance(FileDescriptor targetFileDescriptor) {
        if (targetFileDescriptor == null)
            return;
        Folder parent = targetFileDescriptor.getFolder();
        if (parent == null)
            return;
        Folder current = parent;
        while (current != null) {
            List<Permission> ancestorPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.folder = :folder")
                    .parameter("folder", current)
                    .list();

            if (!ancestorPerms.isEmpty()) {
                for (Permission anc : ancestorPerms) {
                    Permission existing = dataManager.load(Permission.class)
                            .query("select p from Permission p where " +
                                    "(p.user = :user and p.file = :FileDescriptor) or (p.roleCode = :roleCode and p.file = :FileDescriptor)")
                            .parameter("user", anc.getUser())
                            .parameter("roleCode", anc.getRoleCode())
                            .parameter("FileDescriptor", targetFileDescriptor)
                            .optional()
                            .orElse(null);

                    if (existing != null) {
                        existing.setInheritEnabled(true);
                        dataManager.save(existing);
                    } else {
                        Permission inher = dataManager.create(Permission.class);
                        if (anc.getUser() != null)
                            inher.setUser(anc.getUser());
                        else
                            inher.setRoleCode(anc.getRoleCode());
                        inher.setFile(targetFileDescriptor);
                        inher.setPermissionMask(anc.getPermissionMask());
                        inher.setInherited(true);
                        inher.setInheritEnabled(true);
                        inher.setInheritedFrom(ancestorIdentifier(anc)); // full path
                        inher.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                        dataManager.save(inher);
                    }
                }
                return;
            }
            current = current.getParent();
        }
    }

    // replaceChildPermissions (User / Role)
    public void replaceChildPermissions(User user, Folder parent, int parentMask) {
        if (parent == null || user == null) {
            return;
        }
        jdbcTemplate.update(
                "EXEC dbo.usp_PermissionReplaceChildren @FolderId = ?, @ParentMask = ?, @UserId = ?, @RoleCode = ?",
                parent.getId(),
                parentMask,
                user.getId(),
                null);
    }

    public void replaceChildPermissions(ResourceRoleEntity role, Folder parent, int parentMask) {
        if (parent == null || role == null) {
            return;
        }
        jdbcTemplate.update(
                "EXEC dbo.usp_PermissionReplaceChildren @FolderId = ?, @ParentMask = ?, @UserId = ?, @RoleCode = ?",
                parent.getId(),
                parentMask,
                null,
                role.getCode());
    }

    // Helpers
    private Permission findNearestAncestorPermission(User user, Folder start) {
        Folder cur = start;
        while (cur != null) {
            Permission p = loadPermission(user, cur);
            if (p != null)
                return p;
            cur = cur.getParent();
        }
        return null;
    }

    private Permission findNearestAncestorPermission(ResourceRoleEntity role, Folder start) {
        Folder cur = start;
        while (cur != null) {
            Permission p = loadPermission(role, cur);
            if (p != null)
                return p;
            cur = cur.getParent();
        }
        return null;
    }

    public ResourceRoleEntity loadRoleByCode(String roleCode) {
        if (roleCode == null)
            return null;
        return dataManager.load(ResourceRoleEntity.class)
                .query("select r from sec_ResourceRoleEntity r where r.code = :code")
                .parameter("code", roleCode)
                .optional()
                .orElse(null);
    }

    public List<Folder> getAccessibleFolders(User user, SourceStorage sourceStorage) {
        if (user == null || sourceStorage == null)
            return Collections.emptyList();

        // Nếu là admin thì trả hết folder của storage này
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            return dataManager.load(Folder.class)
                    .query("select f from Folder f where f.sourceStorage = :storage and f.inTrash = false")
                    .parameter("storage", sourceStorage)
                    .list();
        }

        // User thường: chỉ trả folder có quyền READ+ trong storage này
        return dataManager.load(Folder.class)
                .query("""
                            select distinct f from Folder f
                            join Permission p on p.folder = f
                            where p.user = :user
                            and f.sourceStorage = :storage
                            and f.inTrash = false
                            and p.permissionMask >= :minMask
                        """)
                .parameter("user", user)
                .parameter("storage", sourceStorage)
                .parameter("minMask", PermissionType.READ.getValue())
                .list();
    }

    public List<FileDescriptor> getAccessibleFiles(User user, SourceStorage sourceStorage, Folder folder) {
        if (user == null || sourceStorage == null)
            return Collections.emptyList();

        // Nếu là admin thì trả hết
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            if (folder == null) {
                return dataManager.load(FileDescriptor.class)
                        .query("select o from FileDescriptor o where o.sourceStorage = :storage and o.inTrash = false and o.folder is null")
                        .parameter("storage", sourceStorage)
                        .list();
            } else {
                return dataManager.load(FileDescriptor.class)
                        .query("select o from FileDescriptor o where o.folder = :folder and o.inTrash = false")
                        .parameter("folder", folder)
                        .list();
            }
        }

        // User thường: chỉ trả file có quyền READ+
        if (folder == null) {
            // Files ở root (không có folder)
            return dataManager.load(FileDescriptor.class)
                    .query("""
                                select distinct o from FileDescriptor o
                                join Permission p on p.file = o
                                where p.user = :user
                                and o.sourceStorage = :storage
                                and o.folder is null
                                and o.inTrash = false
                                and p.permissionMask >= :minMask
                            """)
                    .parameter("user", user)
                    .parameter("storage", sourceStorage)
                    .parameter("minMask", PermissionType.READ.getValue())
                    .list();
        } else {
            // Files trong folder cụ thể
            return dataManager.load(FileDescriptor.class)
                    .query("""
                                select distinct o from FileDescriptor o
                                join Permission p on p.file = o
                                where p.user = :user
                                and o.folder = :folder
                                and o.inTrash = false
                                and p.permissionMask >= :minMask
                            """)
                    .parameter("user", user)
                    .parameter("folder", folder)
                    .parameter("minMask", PermissionType.READ.getValue())
                    .list();
        }
    }

    @Transactional
    public void initializeFilePermission(User user, FileDescriptor fileDescriptor) {
        if (user == null || fileDescriptor == null) {
            return;
        }
        Folder parentFolder = fileDescriptor.getFolder();
        // Find the nearest ancestor permission for this user
        Permission ancestorPerm = findNearestAncestorPermission(user, parentFolder);
        int inheritedMask;
        String inheritedFromValue;
        if (ancestorPerm != null) {
            // Inherit mask from ancestor
            inheritedMask = Optional.ofNullable(ancestorPerm.getPermissionMask()).orElse(0);
            inheritedFromValue = ancestorIdentifier(ancestorPerm);
        } else {
            // No ancestor permission found, grant FULL permission to the uploader
            inheritedMask = PermissionType.FULL.getValue();
            inheritedFromValue = null;
        }
        // Create permission for the file
        Permission filePerm = dataManager.create(Permission.class);
        filePerm.setUser(user);
        filePerm.setFile(fileDescriptor);
        filePerm.setPermissionMask(inheritedMask);
        filePerm.setInherited(ancestorPerm != null); // true if inherited, false if original owner
        filePerm.setInheritEnabled(true);
        filePerm.setInheritedFrom(inheritedFromValue);
        filePerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);

        dataManager.save(filePerm);
    }

    @Transactional
    public void initializeFolderPermission(User user, Folder folder) {
        if (user == null || folder == null)
            return;
        Folder parentFolder = folder.getParent();
        Permission ancestorPerm = findNearestAncestorPermission(user, parentFolder);
        int inheritedMask;
        String inheritedFromValue;
        if (ancestorPerm != null) {
            inheritedMask = Optional.ofNullable(ancestorPerm.getPermissionMask()).orElse(0);
            inheritedFromValue = ancestorIdentifier(ancestorPerm);
        } else {
            inheritedMask = PermissionType.FULL.getValue();
            inheritedFromValue = null;
        }
        Permission folderPerm = dataManager.create(Permission.class);
        folderPerm.setUser(user);
        folderPerm.setFolder(folder);
        folderPerm.setPermissionMask(inheritedMask);
        folderPerm.setInherited(ancestorPerm != null);
        folderPerm.setInheritEnabled(true);
        folderPerm.setInheritedFrom(inheritedFromValue);
        folderPerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
        dataManager.save(folderPerm);
    }
}
