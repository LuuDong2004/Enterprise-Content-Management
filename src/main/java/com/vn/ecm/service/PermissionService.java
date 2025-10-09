package com.vn.ecm.service;

import com.vn.ecm.entity.AppliesTo;
import com.vn.ecm.entity.File;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.Permission;
import com.vn.ecm.entity.PermissionType;
import com.vn.ecm.entity.User;
import io.jmix.core.DataManager;
import io.jmix.securitydata.entity.ResourceRoleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class PermissionService {

    private final DataManager dataManager;

    public PermissionService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    /* ============================
       SAVE permission (User)
       ============================ */

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

    public void savePermission(Collection<Permission> permissions, User user, File file) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(user, file);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setUser(user);
            permission.setFile(file);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);
    }

    /* ============================
       SAVE permission (Role)
       ============================ */

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

    public void savePermission(Collection<Permission> permissions, ResourceRoleEntity role, File file) {
        normalizePermissions(permissions);
        int mask = buildMask(permissions);

        Permission permission = loadPermission(role, file);
        if (permission == null) {
            permission = dataManager.create(Permission.class);
            permission.setRoleCode(role.getCode());
            permission.setFile(file);
        }

        permission.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
        permission.setPermissionMask(mask);
        permission.setInherited(false);
        dataManager.save(permission);
    }

    /* ============================
       LOAD permission
       ============================ */

    public Permission loadPermission(User user, Folder folder) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.folder = :folder")
                .parameter("user", user)
                .parameter("folder", folder)
                .optional()
                .orElse(null);
    }

    public Permission loadPermission(User user, File file) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :file")
                .parameter("user", user)
                .parameter("file", file)
                .optional()
                .orElse(null);
    }

    public Permission loadPermission(ResourceRoleEntity role, Folder folder) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.folder = :folder")
                .parameter("roleCode", role.getCode())
                .parameter("folder", folder)
                .optional()
                .orElse(null);
    }

    public Permission loadPermission(ResourceRoleEntity role, File file) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :file")
                .parameter("roleCode", role.getCode())
                .parameter("file", file)
                .optional()
                .orElse(null);
    }

    /* ============================
       HAS permission
       ============================ */

    /**
     * Check permission on a file (direct file permission or inherited from containing folder chain)
     */
    public boolean hasPermission(User user, PermissionType type, File file) {
        // direct file-level permission
        Permission p = loadPermission(user, file);
        if (p != null && PermissionType.hasPermission(Optional.ofNullable(p.getPermissionMask()).orElse(0), type)) {
            return true;
        }
        // fallback to folder chain
        Folder folder = file.getFolder();
        return folder != null && hasPermission(user, type, folder);
    }

    /**
     * Check permission on a folder by walking up ancestor folders to nearest permission
     * The behavior matches original: find nearest ancestor folder that has permission record;
     * if found, return whether that permission mask includes requested type.
     */
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

    /* ============================
       normalize & mask
       ============================ */

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

    /* ============================
       Propagate to children
       ============================ */

    @Transactional
    protected void propagateToChildren(User user, ResourceRoleEntity role, Folder parentFolder, int parentMask) {
        // fetch children (handle lazy)
        List<Folder> childrenFolders = parentFolder.getChildren();
        List<File> childrenFiles = parentFolder.getFile();

        if (childrenFolders == null) {
            childrenFolders = dataManager.load(Folder.class)
                    .query("select f from Folder f where f.parent = :parent")
                    .parameter("parent", parentFolder)
                    .list();
        }
        if (childrenFiles == null) {
            childrenFiles = dataManager.load(File.class)
                    .query("select o from File o where o.folder = :folder")
                    .parameter("folder", parentFolder)
                    .list();
        }

        String inheritedFromValue = "FOLDER:" + parentFolder.getId().toString();

        // files - CHỈ CÂP NHẬT NẾU ĐÃ TỒN TẠI
        for (File childFile : childrenFiles) {
            Permission perm = (user != null) ? loadPermission(user, childFile) : loadPermission(role, childFile);

            // THAY ĐỔI: Chỉ cập nhật nếu permission đã tồn tại
            if (perm == null) {
                continue; // Bỏ qua, không tạo mới
            }

            if (Boolean.FALSE.equals(perm.getInheritEnabled())) {
                continue; // Không cập nhật nếu đã tắt kế thừa
            }

            int currentMask = perm.getPermissionMask() == null ? 0 : perm.getPermissionMask();
            int newMask = computeNewMask(parentMask, currentMask);

            perm.setPermissionMask(newMask);
            perm.setInherited(true);
            perm.setInheritEnabled(true);
            perm.setInheritedFrom(inheritedFromValue);
            perm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
            dataManager.save(perm);
        }

        // folders - CHỈ CÂP NHẬT NẾU ĐÃ TỒN TẠI
        for (Folder child : childrenFolders) {
            Permission perm = (user != null) ? loadPermission(user, child) : loadPermission(role, child);

            if (perm == null) {
                continue; // Bỏ qua, không tạo mới
            }

            if (Boolean.FALSE.equals(perm.getInheritEnabled())) {
                continue; // Không cập nhật nếu đã tắt kế thừa
            }

            int currentMask = perm.getPermissionMask() == null ? 0 : perm.getPermissionMask();
            int newMask = computeNewMask(parentMask, currentMask);

            perm.setPermissionMask(newMask);
            perm.setInherited(true);
            perm.setInheritEnabled(true);
            perm.setInheritedFrom(inheritedFromValue);
            perm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
            dataManager.save(perm);

            // Đệ quy - chỉ cập nhật các permission đã tồn tại ở cấp sâu hơn
            propagateToChildren(user, role, child, parentMask);
        }
    }

    private int computeNewMask(int parentMask, int currentMask) {
        int newMask = currentMask;
        if ((parentMask & PermissionType.FULL.getValue()) == PermissionType.FULL.getValue()) {
            newMask = PermissionType.FULL.getValue();
        } else {
            for (PermissionType pt : PermissionType.values()) {
                if (pt == PermissionType.FULL) continue;
                int bit = pt.getValue();
                if ((parentMask & bit) == bit) newMask |= bit;
                else newMask &= ~bit;
            }
        }
        return newMask;
    }

    /* ============================
       Disable / Enable inheritance (User)
       ============================ */

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
                    perm.setInherited(false);
                    dataManager.save(perm);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    @Transactional
    public void disableInheritance(User user, File file, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :file")
                .parameter("user", user)
                .parameter("file", file)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);

            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    perm.setInherited(false);
                    dataManager.save(perm);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

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
                    perm.setInherited(false);
                    dataManager.save(perm);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    @Transactional
    public void disableInheritance(ResourceRoleEntity role, File file, boolean convertToExplicit) {
        List<Permission> permissions = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :file")
                .parameter("roleCode", role.getCode())
                .parameter("file", file)
                .list();

        for (Permission perm : permissions) {
            perm.setInheritEnabled(false);

            if (Boolean.TRUE.equals(perm.getInherited())) {
                if (convertToExplicit) {
                    perm.setInherited(false);
                    dataManager.save(perm);
                } else {
                    dataManager.remove(perm);
                }
            } else {
                dataManager.save(perm);
            }
        }
    }

    /* ============================
       Enable inheritance (User / Role)
       - find nearest ancestor that has permission
       - clone as inherited at node if not exists
       - propagate down
       ============================ */

    @Transactional
    public void enableInheritance(User user, Folder folder) {
        // enable any existing perms at node
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
            // propagate using ancestor's mask
            propagateToChildren(user, null, folder, ancestor.getPermissionMask());
        }
    }

    @Transactional
    public void enableInheritance(User user, File file) {
        // enable perms at node
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.file = :file")
                .parameter("user", user)
                .parameter("file", file)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }

        Folder start = file.getFolder();
        Permission ancestor = findNearestAncestorPermission(user, start);
        if (ancestor != null) {
            Permission nodePerm = loadPermission(user, file);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setUser(user);
                nodePerm.setFile(file);
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
    public void enableInheritance(ResourceRoleEntity role, File file) {
        List<Permission> perms = dataManager.load(Permission.class)
                .query("select p from Permission p where p.roleCode = :roleCode and p.file = :file")
                .parameter("roleCode", role.getCode())
                .parameter("file", file)
                .list();
        for (Permission p : perms) {
            p.setInheritEnabled(true);
            dataManager.save(p);
        }

        Folder start = file.getFolder();
        Permission ancestor = findNearestAncestorPermission(role, start);
        if (ancestor != null) {
            Permission nodePerm = loadPermission(role, file);
            if (nodePerm == null) {
                nodePerm = dataManager.create(Permission.class);
                nodePerm.setRoleCode(role.getCode());
                nodePerm.setFile(file);
                nodePerm.setPermissionMask(ancestor.getPermissionMask());
                nodePerm.setInherited(true);
                nodePerm.setInheritEnabled(true);
                nodePerm.setInheritedFrom(ancestorIdentifier(ancestor));
                nodePerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                dataManager.save(nodePerm);
            }
        }
    }

    /* ============================
       Enable remove inheritance
       (clone from nearest ancestor when node had no permission rows)
       - accepts Folder or File target
       ============================ */

    @Transactional
    public void enableRemoveInheritance(Folder targetFolder) {
        if (targetFolder == null) return;
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
                        if (anc.getUser() != null) inher.setUser(anc.getUser());
                        else inher.setRoleCode(anc.getRoleCode());
                        inher.setFolder(targetFolder);
                        inher.setPermissionMask(anc.getPermissionMask());
                        inher.setInherited(true);
                        inher.setInheritEnabled(true);
                        inher.setInheritedFrom(ancestorIdentifier(anc));
                        inher.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
                        dataManager.save(inher);
                    }

                    // propagate
                    if (anc.getUser() != null) propagateToChildren(anc.getUser(), null, targetFolder, anc.getPermissionMask());
                    else {
                        ResourceRoleEntity roleEntity = loadRoleByCode(anc.getRoleCode());
                        if (roleEntity != null) propagateToChildren(null, roleEntity, targetFolder, anc.getPermissionMask());
                    }
                }
                return;
            }
            current = current.getParent();
        }
    }

    @Transactional
    public void enableRemoveInheritance(File targetFile) {
        if (targetFile == null) return;
        Folder parent = targetFile.getFolder();
        if (parent == null) return;
        // find ancestor perms up the folder chain
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
                                    "(p.user = :user and p.file = :file) or (p.roleCode = :roleCode and p.file = :file)")
                            .parameter("user", anc.getUser())
                            .parameter("roleCode", anc.getRoleCode())
                            .parameter("file", targetFile)
                            .optional()
                            .orElse(null);

                    if (existing != null) {
                        existing.setInheritEnabled(true);
                        dataManager.save(existing);
                    } else {
                        Permission inher = dataManager.create(Permission.class);
                        if (anc.getUser() != null) inher.setUser(anc.getUser());
                        else inher.setRoleCode(anc.getRoleCode());
                        inher.setFile(targetFile);
                        inher.setPermissionMask(anc.getPermissionMask());
                        inher.setInherited(true);
                        inher.setInheritEnabled(true);
                        inher.setInheritedFrom(ancestorIdentifier(anc));
                        inher.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
                        dataManager.save(inher);
                    }
                    // propagate only relevant for folders (targetFile not folder) - no recursion here
                }
                return;
            }
            current = current.getParent();
        }
    }

    /* ============================
       replaceChildPermissions (User / Role)
       - now accept Folder parent (no path strings)
       ============================ */

    @Transactional
    public void replaceChildPermissions(User user, Folder parent, int parentMask) {
        if (parent == null) return;

        List<Folder> childrenFolders = parent.getChildren();
        List<File> childrenFiles = parent.getFile();

        if (childrenFolders == null) {
            childrenFolders = dataManager.load(Folder.class)
                    .query("select f from Folder f where f.parent = :parent")
                    .parameter("parent", parent)
                    .list();
        }
        if (childrenFiles == null) {
            childrenFiles = dataManager.load(File.class)
                    .query("select o from File o where o.folder = :folder")
                    .parameter("folder", parent)
                    .list();
        }

        // files
        for (File childFile : childrenFiles) {
            List<Permission> existingPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.user = :user and p.file = :file")
                    .parameter("user", user)
                    .parameter("file", childFile)
                    .list();
            for (Permission e : existingPerms) dataManager.remove(e);

            Permission childPerm = dataManager.create(Permission.class);
            childPerm.setUser(user);
            childPerm.setFile(childFile);
            childPerm.setPermissionMask(parentMask);
            childPerm.setInherited(true);
            childPerm.setInheritEnabled(true);
            childPerm.setInheritedFrom("FOLDER:" + parent.getId());
            childPerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
            dataManager.save(childPerm);
        }

        // folders
        for (Folder childFolder : childrenFolders) {
            List<Permission> existingPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.user = :user and p.folder = :folder")
                    .parameter("user", user)
                    .parameter("folder", childFolder)
                    .list();
            for (Permission e : existingPerms) dataManager.remove(e);

            Permission childPerm = dataManager.create(Permission.class);
            childPerm.setUser(user);
            childPerm.setFolder(childFolder);
            childPerm.setPermissionMask(parentMask);
            childPerm.setInherited(true);
            childPerm.setInheritEnabled(true);
            childPerm.setInheritedFrom("FOLDER:" + parent.getId());
            childPerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
            dataManager.save(childPerm);

            // recurse
            replaceChildPermissions(user, childFolder, parentMask);
        }
    }

    @Transactional
    public void replaceChildPermissions(ResourceRoleEntity role, Folder parent, int parentMask) {
        if (parent == null) return;

        List<Folder> childrenFolders = parent.getChildren();
        List<File> childrenFiles = parent.getFile();

        if (childrenFolders == null) {
            childrenFolders = dataManager.load(Folder.class)
                    .query("select f from Folder f where f.parent = :parent")
                    .parameter("parent", parent)
                    .list();
        }
        if (childrenFiles == null) {
            childrenFiles = dataManager.load(File.class)
                    .query("select o from File o where o.folder = :folder")
                    .parameter("folder", parent)
                    .list();
        }

        for (File childFile : childrenFiles) {
            List<Permission> existingPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.roleCode = :roleCode and p.file = :file")
                    .parameter("roleCode", role.getCode())
                    .parameter("file", childFile)
                    .list();
            for (Permission e : existingPerms) dataManager.remove(e);

            Permission childPerm = dataManager.create(Permission.class);
            childPerm.setRoleCode(role.getCode());
            childPerm.setFile(childFile);
            childPerm.setPermissionMask(parentMask);
            childPerm.setInherited(true);
            childPerm.setInheritEnabled(true);
            childPerm.setInheritedFrom("FOLDER:" + parent.getId());
            childPerm.setAppliesTo(AppliesTo.THIS_FOLDER_ONLY);
            dataManager.save(childPerm);
        }

        for (Folder childFolder : childrenFolders) {
            List<Permission> existingPerms = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.roleCode = :roleCode and p.folder = :folder")
                    .parameter("roleCode", role.getCode())
                    .parameter("folder", childFolder)
                    .list();
            for (Permission e : existingPerms) dataManager.remove(e);

            Permission childPerm = dataManager.create(Permission.class);
            childPerm.setRoleCode(role.getCode());
            childPerm.setFolder(childFolder);
            childPerm.setPermissionMask(parentMask);
            childPerm.setInherited(true);
            childPerm.setInheritEnabled(true);
            childPerm.setInheritedFrom("FOLDER:" + parent.getId());
            childPerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES);
            dataManager.save(childPerm);

            replaceChildPermissions(role, childFolder, parentMask);
        }
    }

    /* ============================
       Helpers
       - nearest ancestor permission (folder chain)
       - ancestor identifier string
       ============================ */

    private Permission findNearestAncestorPermission(User user, Folder start) {
        Folder cur = start;
        while (cur != null) {
            Permission p = loadPermission(user, cur);
            if (p != null) return p;
            cur = cur.getParent();
        }
        return null;
    }

    private Permission findNearestAncestorPermission(ResourceRoleEntity role, Folder start) {
        Folder cur = start;
        while (cur != null) {
            Permission p = loadPermission(role, cur);
            if (p != null) return p;
            cur = cur.getParent();
        }
        return null;
    }

    private String ancestorIdentifier(Permission anc) {
        if (anc == null) return null;
        if (anc.getFolder() != null) {
            return "FOLDER:" + anc.getFolder().getId().toString();
        }
        if (anc.getFile() != null) {
            return "FILE:" + anc.getFile().getId().toString();
        }
        return null;
    }

    public ResourceRoleEntity loadRoleByCode(String roleCode) {
        if (roleCode == null) return null;
        return dataManager.load(ResourceRoleEntity.class)
                .query("select r from sec_ResourceRoleEntity r where r.code = :code")
                .parameter("code", roleCode)
                .optional()
                .orElse(null);
    }


}
