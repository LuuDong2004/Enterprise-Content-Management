package com.vn.ecm.service.ecm;

import com.vn.ecm.entity.*;

import io.jmix.core.DataManager;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PermissionService {

    private final DataManager dataManager;

    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public PermissionService(DataManager dataManager, JdbcTemplate jdbcTemplate) {
        this.dataManager = dataManager;
        this.jdbcTemplate = jdbcTemplate;
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
        // Duyệt theo từng cấp với batch processing
        Set<UUID> currentLevelIds = new HashSet<>();
        currentLevelIds.add(parentFolder.getId());
        while (!currentLevelIds.isEmpty()) {
            if (currentLevelIds.isEmpty()) {
                break;
            }
            // Build IN clause cho parent IDs
            StringBuilder parentIdList = new StringBuilder();
            int count = 0;
            for (UUID id : currentLevelIds) {
                if (count > 0)
                    parentIdList.append(",");
                parentIdList.append("'").append(id.toString()).append("'");
                count++;
            }
            // Lấy tất cả folder con của current level cùng lúc
            String childQuery = "SELECT F.ID, " +
                    "  CASE WHEN EXISTS ( " +
                    "    SELECT 1 FROM PERMISSION P " +
                    "    WHERE P.FOLDER_ID = F.ID " +
                    "    AND P." + (userId != null ? "USER_ID" : "ROLE_CODE") + " = ? " +
                    "    AND P.INHERIT_ENABLED = 0 " +
                    "  ) THEN 1 ELSE 0 END AS IS_BLOCKED " +
                    "FROM FOLDER F " +
                    "WHERE F.PARENT_ID IN (" + parentIdList + ")";
            List<Object[]> children = entityManager.createNativeQuery(childQuery)
                    .setParameter(1, userId != null ? userId : roleCode)
                    .getResultList();

            if (children.isEmpty()) {
                break;
            }
            // Phân loại folder: valid vs blocked
            List<UUID> validFolderIds = new ArrayList<>();
            Set<UUID> nextLevelIds = new HashSet<>();
            for (Object[] row : children) {
                Object rawId = row[0];
                UUID childId = rawId instanceof UUID ? (UUID) rawId : UUID.fromString(String.valueOf(rawId));
                int isBlocked = ((Number) row[1]).intValue();
                if (isBlocked == 0) {
                    validFolderIds.add(childId);
                    nextLevelIds.add(childId);
                }
            }
            // Batch update và insert cho các folder hợp lệ
            if (!validFolderIds.isEmpty()) {
                StringBuilder validIdList = new StringBuilder();
                count = 0;
                for (UUID id : validFolderIds) {
                    if (count > 0)
                        validIdList.append(",");
                    validIdList.append("'").append(id.toString()).append("'");
                    count++;
                }
                if (userId != null) {
                    // Batch UPDATE
                    entityManager.createNativeQuery(
                            "UPDATE PERMISSION " +
                                    "SET PERMISSION_MASK = ?, INHERITED = 1, INHERITED_FROM = ? " +
                                    "WHERE USER_ID = ? " +
                                    "AND (INHERIT_ENABLED = 1 OR INHERIT_ENABLED IS NULL) " +
                                    "AND FOLDER_ID IN (" + validIdList + ")")
                            .setParameter(1, parentMask)
                            .setParameter(2, getFullPath(parentFolder))
                            .setParameter(3, userId)
                            .executeUpdate();
                    // Batch INSERT
                    entityManager.createNativeQuery(
                            "INSERT INTO PERMISSION (ID, USER_ID, FOLDER_ID, PERMISSION_MASK, INHERITED, INHERIT_ENABLED, INHERITED_FROM, APPLIES_TO) "
                                    +
                                    "SELECT NEWID(), ?, F.ID, ?, 1, 1, ?, 0 " +
                                    "FROM FOLDER F " +
                                    "WHERE F.ID IN (" + validIdList + ") " +
                                    "AND NOT EXISTS ( " +
                                    "  SELECT 1 FROM PERMISSION P " +
                                    "  WHERE P.FOLDER_ID = F.ID AND P.USER_ID = ? " +
                                    ")")
                            .setParameter(1, userId)
                            .setParameter(2, parentMask)
                            .setParameter(3, getFullPath(parentFolder))
                            .setParameter(4, userId)
                            .executeUpdate();
                } else {
                    // Batch UPDATE cho Role
                    entityManager.createNativeQuery(
                            "UPDATE PERMISSION " +
                                    "SET PERMISSION_MASK = ?, INHERITED = 1, INHERITED_FROM = ? " +
                                    "WHERE ROLE_CODE = ? " +
                                    "AND (INHERIT_ENABLED = 1 OR INHERIT_ENABLED IS NULL) " +
                                    "AND FOLDER_ID IN (" + validIdList + ")")
                            .setParameter(1, parentMask)
                            .setParameter(2, getFullPath(parentFolder))
                            .setParameter(3, roleCode)
                            .executeUpdate();

                    // Batch INSERT cho Role
                    entityManager.createNativeQuery(
                            "INSERT INTO PERMISSION (ID, ROLE_CODE, FOLDER_ID, PERMISSION_MASK, INHERITED, INHERIT_ENABLED, INHERITED_FROM, APPLIES_TO) "
                                    +
                                    "SELECT NEWID(), ?, F.ID, ?, 1, 1, ?, 0 " +
                                    "FROM FOLDER F " +
                                    "WHERE F.ID IN (" + validIdList + ") " +
                                    "AND NOT EXISTS ( " +
                                    "  SELECT 1 FROM PERMISSION P " +
                                    "  WHERE P.FOLDER_ID = F.ID AND P.ROLE_CODE = ? " +
                                    ")")
                            .setParameter(1, roleCode)
                            .setParameter(2, parentMask)
                            .setParameter(3, getFullPath(parentFolder))
                            .setParameter(4, roleCode)
                            .executeUpdate();
                }
            }
            // Chuyển sang level tiếp theo
            currentLevelIds = nextLevelIds;
        }
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
                    perm.setInheritEnabled(false);
                    dataManager.save(perm);
                    int explicitMask = Optional.ofNullable(perm.getPermissionMask()).orElse(0);
                    // Push the explicit mask down so children stop inheriting from higher ancestors
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
                    perm.setInheritEnabled(false);
                    dataManager.save(perm);
                    int explicitMask = Optional.ofNullable(perm.getPermissionMask()).orElse(0);
                    // Push the explicit mask down so children stop inheriting from higher ancestors
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

    @Transactional
    public void replaceChildPermissions(User user, Folder parent, int parentMask) {
        if (parent == null || user == null) {
            return;
        }
        // Lấy FULL_PATH của parent folder
        String parentPath = parent.getFullPath();
        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }
        String pathPrefix = parentPath.endsWith("/") ? parentPath : parentPath + "/";
        entityManager.createNativeQuery(
                "DELETE FROM PERMISSION " +
                        "WHERE FOLDER_ID IN (" +
                        "    SELECT ID FROM FOLDER " +
                        "    WHERE FULL_PATH LIKE ?1 ESCAPE '\\' " +
                        "    AND ID != ?2" +
                        ") " +
                        "AND USER_ID = ?3 " +
                        "AND FOLDER_ID IS NOT NULL " +
                        "AND INHERITED = 0")
                .setParameter(1, pathPrefix + "%")
                .setParameter(2, parent.getId())
                .setParameter(3, user.getId())
                .executeUpdate();
        // Cập nhật inherited permissions cho children đã có permission
        entityManager.createNativeQuery(
                "UPDATE PERMISSION " +
                        "SET PERMISSION_MASK = ?1, " +
                        "    INHERITED = 1, " +
                        "    INHERITED_FROM = ?2, " +
                        "    INHERIT_ENABLED = 1 " +
                        "WHERE FOLDER_ID IN (" +
                        "    SELECT ID FROM FOLDER " +
                        "    WHERE FULL_PATH LIKE ?3 ESCAPE '\\' " +
                        "    AND ID != ?4" +
                        ") " +
                        "AND USER_ID = ?5 " +
                        "AND FOLDER_ID IS NOT NULL")
                .setParameter(1, parentMask)
                .setParameter(2, parentPath)
                .setParameter(3, pathPrefix + "%")
                .setParameter(4, parent.getId())
                .setParameter(5, user.getId())
                .executeUpdate();
        // Tạo inherited permissions mới cho children chưa có permission
        entityManager.createNativeQuery(
                "INSERT INTO PERMISSION (ID, USER_ID, FOLDER_ID, PERMISSION_MASK, INHERITED, INHERIT_ENABLED, INHERITED_FROM, APPLIES_TO) "
                        +
                        "SELECT NEWID(), ?1, F.ID, ?2, 1, 1, ?3, 0 " +
                        "FROM FOLDER F " +
                        "WHERE F.FULL_PATH LIKE ?4 ESCAPE '\\' " +
                        "AND F.ID != ?5 " +
                        "AND NOT EXISTS (" +
                        "    SELECT 1 FROM PERMISSION P " +
                        "    WHERE P.FOLDER_ID = F.ID AND P.USER_ID = ?1" +
                        ")")
                .setParameter(1, user.getId())
                .setParameter(2, parentMask)
                .setParameter(3, parentPath)
                .setParameter(4, pathPrefix + "%")
                .setParameter(5, parent.getId())
                .executeUpdate();
    }

    @Transactional
    public void replaceChildPermissions(ResourceRoleEntity role, Folder parent, int parentMask) {
        if (parent == null || role == null) {
            return;
        }
        // Lấy FULL_PATH của parent folder
        String parentPath = parent.getFullPath();
        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }
        String pathPrefix = parentPath.endsWith("/") ? parentPath : parentPath + "/";
        // Xóa tất cả explicit permissions (không inherited) của children
        entityManager.createNativeQuery(
                "DELETE FROM PERMISSION " +
                        "WHERE FOLDER_ID IN (" +
                        "    SELECT ID FROM FOLDER " +
                        "    WHERE FULL_PATH LIKE ?1 ESCAPE '\\' " +
                        "    AND ID != ?2" +
                        ") " +
                        "AND ROLE_CODE = ?3 " +
                        "AND FOLDER_ID IS NOT NULL " +
                        "AND INHERITED = 0")
                .setParameter(1, pathPrefix + "%")
                .setParameter(2, parent.getId())
                .setParameter(3, role.getCode())
                .executeUpdate();
        // Cập nhật inherited permissions cho children đã có permission
        entityManager.createNativeQuery(
                "UPDATE PERMISSION " +
                        "SET PERMISSION_MASK = ?1, " +
                        "    INHERITED = 1, " +
                        "    INHERITED_FROM = ?2, " +
                        "    INHERIT_ENABLED = 1 " +
                        "WHERE FOLDER_ID IN (" +
                        "    SELECT ID FROM FOLDER " +
                        "    WHERE FULL_PATH LIKE ?3 ESCAPE '\\' " +
                        "    AND ID != ?4" +
                        ") " +
                        "AND ROLE_CODE = ?5 " +
                        "AND FOLDER_ID IS NOT NULL")
                .setParameter(1, parentMask)
                .setParameter(2, parentPath)
                .setParameter(3, pathPrefix + "%")
                .setParameter(4, parent.getId())
                .setParameter(5, role.getCode())
                .executeUpdate();

        // Tạo inherited permissions mới cho children chưa có permission
        entityManager.createNativeQuery(
                "INSERT INTO PERMISSION (ID, ROLE_CODE, FOLDER_ID, PERMISSION_MASK, INHERITED, INHERIT_ENABLED, INHERITED_FROM, APPLIES_TO) "
                        +
                        "SELECT NEWID(), ?1, F.ID, ?2, 1, 1, ?3, 0 " +
                        "FROM FOLDER F " +
                        "WHERE F.FULL_PATH LIKE ?4 ESCAPE '\\' " +
                        "AND F.ID != ?5 " +
                        "AND NOT EXISTS (" +
                        "    SELECT 1 FROM PERMISSION P " +
                        "    WHERE P.FOLDER_ID = F.ID AND P.ROLE_CODE = ?1" +
                        ")")
                .setParameter(1, role.getCode())
                .setParameter(2, parentMask)
                .setParameter(3, parentPath)
                .setParameter(4, pathPrefix + "%")
                .setParameter(5, parent.getId())
                .executeUpdate();
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

    private boolean hasFullAccessRole(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM SEC_ROLE_ASSIGNMENT " +
                            "WHERE USERNAME = ? AND ROLE_CODE = ? AND ROLE_TYPE = 'resource'",
                    Long.class,
                    user.getUsername(),
                    "system-full-access");
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Folder> getAccessibleFolders(User user, SourceStorage sourceStorage) {
        if (user == null || sourceStorage == null)
            return Collections.emptyList();
        // Nếu user có role system-full-access thì trả hết folder của storage này
        if (hasFullAccessRole(user)) {
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
        // Nếu user có role system-full-access thì trả hết
        if (hasFullAccessRole(user)) {
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
            // Files ở root
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
