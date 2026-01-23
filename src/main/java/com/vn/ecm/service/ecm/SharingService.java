package com.vn.ecm.service.ecm;

import com.vn.ecm.component.DriveItemTypeResolver;
import com.vn.ecm.dto.ShareLinkItemDto;
import com.vn.ecm.dto.UserPermissionDto;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.user.UserService;
import io.jmix.core.DataManager;
import io.jmix.core.entity.EntityValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service("ecm_SharingService")
public class SharingService {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private UserService userService;

    @Autowired
    private DriveItemTypeResolver driveItemTypeResolver;

    private final SecureRandom secureRandom = new SecureRandom();
    public static final String ANYONE_WITH_LINK_VALUE = "ANYONE_WITH_LINK";

    /**
     * Share a Folder or File with a specific User.
     * Creates or updates an explicit Permission record.
     */
    @Transactional
    public void shareWithUser(User owner, User recipient, Object target, PermissionType permissionType) {
        validateOwnerAccess(owner, target);
        
        Permission existingPerm = findExplicitPermission(recipient, target);
        if (existingPerm != null) {
            // Update existing
            existingPerm.setPermissionMask(permissionType.getValue());
            dataManager.save(existingPerm);
        } else {
            // Create new
            Permission newPerm = dataManager.create(Permission.class);
            newPerm.setUser(recipient);
            setTarget(newPerm, target);
            newPerm.setPermissionMask(permissionType.getValue());
            newPerm.setInheritEnabled(true);
            newPerm.setInherited(false);
            newPerm.setAppliesTo(AppliesTo.THIS_FOLDER_SUBFOLDERS_FILES); // Default scope
            dataManager.save(newPerm);
        }
    }

    /**
     * Share by Link (Anyone with the link).
     * Creates a ShareLink record and an anonymous Permission.
     */
    @Transactional
    public ShareLink shareByLink(User owner, Object target, PermissionType permissionType, LocalDateTime expiryDate) {
        validateOwnerAccess(owner, target);

        // 1. Create ShareLink
        ShareLink link = dataManager.create(ShareLink.class);
        link.setToken(generateUniqueToken());
        link.setOwner(owner);
        link.setCreatedDate(LocalDateTime.now());
        link.setExpiryDate(expiryDate);
        link.setActive(true);
        link.setPermissionType(permissionType.name()); // Store name e.g. "READ"
        if (target instanceof Folder) {
            link.setFolder((Folder) target);
        } else if (target instanceof FileDescriptor) {
            link.setFile((FileDescriptor) target);
        }
        dataManager.save(link);

        // 2. Ensure "Anyone" Permission exists on the target
        ensureAnyonePermission(target, permissionType);

        return link;
    }

    /**
     * Stop sharing with a specific User.
     */
    @Transactional
    public void unshareUser(User owner, User recipient, Object target) {
        validateOwnerAccess(owner, target);
        Permission perm = findExplicitPermission(recipient, target);
        if (perm != null) {
            dataManager.remove(perm);
        }
    }

    /**
     * Stop sharing a specific Link.
     */
    @Transactional
    public void unshareLink(User owner, String token) {
        ShareLink link = dataManager.load(ShareLink.class)
                .query("select s from ShareLink s where s.token = :token")
                .parameter("token", token)
                .optional()
                .orElse(null);
        
        if (link != null) {
            // Check ownership (security)
            if (!link.getOwner().equals(owner)) {
                 // Or throw exception
                 return;
            }
            link.setActive(false);
            dataManager.save(link);
        }
    }

    public List<ShareLinkItemDto> getSharedWithMe(User currentUser) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.user = :user and p.inherited = false")
                .parameter("user", currentUser)
                .fetchPlan(fp -> {
                    fp.add("permissionMask");
                    fp.add("folder", f -> {
                        f.add("id");
                        f.add("name");
                        f.add("createdDate");
                    });
                    fp.add("file", f -> {
                        f.add("id");
                        f.add("name");
                        f.add("createBy");
                        f.add("lastModified");
                        f.add("extension");
                    });
                })
                .list()
                .stream()
                .map(p -> {
                    boolean isFolder = p.getFolder() != null;
                    if (isFolder) {
                        Folder f = p.getFolder();
                        String ownerName = resolveFolderOwnerName(f);
                        if (ownerName.equals(currentUser.getUsername())) {
                            return null;
                        }
                        return new ShareLinkItemDto(
                                f.getId(),
                                f.getName(),
                                DriveItemType.FOLDER,
                                null,
                                ownerName,
                                f.getCreatedDate(),
                                PermissionType.fromId(p.getPermissionMask()) != null ?
                                        PermissionType.fromId(p.getPermissionMask()).name() : "READ",
                                true
                        );
                    } else if (p.getFile() != null) {
                        FileDescriptor fd = p.getFile();
                        if (fd.getCreateBy() != null && fd.getCreateBy().equals(currentUser.getUsername())) {
                            return null;
                        }
                        return new ShareLinkItemDto(
                                fd.getId(),
                                fd.getName(),
                                driveItemTypeResolver.resolve(fd),
                                fd.getExtension(),
                                fd.getCreateBy() != null ? fd.getCreateBy() : "System",
                                fd.getLastModified(),
                                PermissionType.fromId(p.getPermissionMask()) != null ?
                                        PermissionType.fromId(p.getPermissionMask()).name() : "READ",
                                true
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String resolveFolderOwnerName(Folder folder) {
        return dataManager.load(Permission.class)
                .query("select p from Permission p where p.folder = :folder and p.inherited = false and p.permissionMask = :mask")
                .parameter("folder", folder)
                .parameter("mask", PermissionType.FULL.getValue())
                .maxResults(1)
                .optional()
                .map(p -> p.getUser() != null ? p.getUser().getUsername() : "System")
                .orElse("System");
    }

    /**
     * Get all items shared with the current user.
     * Logic: explicit permission exists AND user is not owner.
     */
    public List<ShareLinkItemDto> getSharedLinks(User user) {

        List<ShareLink> links = dataManager.load(ShareLink.class)
                .query("select s from ShareLink s where s.owner = :user")
                .parameter("user", user)
                .fetchPlan(fp -> {
                    fp.add("createdDate");
                    fp.add("permissionType");
                    fp.add("active");
                    fp.add("expiryDate");

                    fp.add("owner", o -> o.add("username"));

                    fp.add("folder", f -> {
                        f.add("id");
                        f.add("name");
                    });

                    fp.add("file", f -> {
                        f.add("id");
                        f.add("name");
                        f.add("extension");
                    });
                })
                .list();

        return links.stream().map(s -> {
            boolean isFolder = s.getFolder() != null;

            return new ShareLinkItemDto(
                    s.getId(),
                    isFolder ? s.getFolder().getName() : s.getFile().getName(),
                    isFolder ? DriveItemType.FOLDER : driveItemTypeResolver.resolve(s.getFile()),
                    isFolder ? null : s.getFile().getExtension(),
                    s.getOwner().getUsername(),
                    s.getCreatedDate(),
                    s.getPermissionType(),
                    s.isValid()
            );
        }).toList();
    }


    /**
     * Runtime Permission Resolution (Bottom-Up).
     * Returns the effective PermissionType for a user on a target.
     */
    public PermissionType resolvePermission(User user, Object target) {
        if (target == null) return null;
        
        Object current = target;
        while (current != null) {
            // 1. Check explicit user permission
            Permission explicit = findExplicitPermission(user, current);
            if (explicit != null) {
                return PermissionType.fromId(explicit.getPermissionMask());
            }
            
            // 2. Move to parent
            current = getParent(current);
        }
        
        return null; // No access found
    }

    /**
     * Resolve Permission for a Link Token (Anonymous access).
     */
    public PermissionType resolveLinkPermission(String token, Object target) {
        ShareLink link = dataManager.load(ShareLink.class)
                .query("select s from ShareLink s where s.token = :token and s.active = true")
                .parameter("token", token)
                .optional()
                .orElse(null);
                
        if (link == null || link.isExpired()) return null;
        
        // Ensure the link actually points to the target or an ancestor of the target
        if (isDescendantOrSelf(link, target)) {
             Object current = target;
             while (current != null) {
                 Permission anonPerm = findAnyonePermission(current);
                 if (anonPerm != null) {
                     return PermissionType.fromId(anonPerm.getPermissionMask());
                 }
                 current = getParent(current);
             }
        }
        return null;
    }

    // --- Helpers ---

    private void validateOwnerAccess(User user, Object target) {
        // Implement logic: check if user is Owner/Creator or has FULL permission
    }

    private Permission findExplicitPermission(User user, Object target) {
        if (user == null) return null;
        String query = "select p from Permission p where p.user = :user ";
        if (target instanceof Folder) {
            query += "and p.folder = :target";
        } else {
            query += "and p.file = :target";
        }
        return dataManager.load(Permission.class)
                .query(query)
                .parameter("user", user)
                .parameter("target", target)
                .optional()
                .orElse(null);
    }
    
    private Permission findAnyonePermission(Object target) {
        String query = "select p from Permission p where p.user is null ";
        if (target instanceof Folder) {
            query += "and p.folder = :target";
        } else {
            query += "and p.file = :target";
        }
        
        List<Permission> candidates = dataManager.load(Permission.class)
                .query(query)
                .parameter("target", target)
                .list();
                
        for (Permission p : candidates) {
            Object rawVal = EntityValues.getValue(p, "appliesTo");
            if (ANYONE_WITH_LINK_VALUE.equals(rawVal) || 
               (rawVal instanceof String && ((String)rawVal).contains("ANYONE"))) {
                return p;
            }
        }
        return null;
    }

    private void ensureAnyonePermission(Object target, PermissionType type) {
        Permission existing = findAnyonePermission(target);
        if (existing == null) {
            Permission newPerm = dataManager.create(Permission.class);
            newPerm.setUser(null);
            setTarget(newPerm, target);
            newPerm.setPermissionMask(type.getValue());
            newPerm.setInheritEnabled(true);
            newPerm.setInherited(false);
             EntityValues.setValue(newPerm, "appliesTo", ANYONE_WITH_LINK_VALUE);
            dataManager.save(newPerm);
        } else {
            if (existing.getPermissionMask() < type.getValue()) {
                existing.setPermissionMask(type.getValue());
                dataManager.save(existing);
            }
        }
    }

    private void setTarget(Permission p, Object target) {
        if (target instanceof Folder) {
            p.setFolder((Folder) target);
        } else if (target instanceof FileDescriptor) {
            p.setFile((FileDescriptor) target);
        }
    }

    private Object getParent(Object current) {
        if (current instanceof Folder) {
            return ((Folder) current).getParent();
        } else if (current instanceof FileDescriptor) {
            return ((FileDescriptor) current).getFolder();
        }
        return null;
    }
    
    private boolean isDescendantOrSelf(ShareLink link, Object target) {
        if (link.getFolder() != null && target instanceof Folder) {
            Folder cur = (Folder) target;
            while(cur != null) {
                if (cur.getId().equals(link.getFolder().getId())) return true;
                cur = cur.getParent();
            }
        } else if (link.getFolder() != null && target instanceof FileDescriptor) {
            Folder cur = ((FileDescriptor) target).getFolder();
             while(cur != null) {
                if (cur.getId().equals(link.getFolder().getId())) return true;
                cur = cur.getParent();
            }
        } else if (link.getFile() != null && target instanceof FileDescriptor) {
            return ((FileDescriptor) target).getId().equals(link.getFile().getId());
        }
        return false;
    }
    
    public List<User> searchUsers(String query) {
        return dataManager.load(User.class)
                .query("select u from User u where u.username like :query or u.email like :query")
                .parameter("query", "%" + query + "%")
                .maxResults(10)
                .list();
    }

    public Optional<User> findUserByUsernameOrEmail(String identifier) {
        return dataManager.load(User.class)
                .query("select u from User u where u.username = :id or u.email = :id")
                .parameter("id", identifier)
                .optional();
    }

    public List<UserPermissionDto> getPermissions(UUID targetId, String targetType) {
        List<UserPermissionDto> result = new ArrayList<>();
        // Get current authenticated user
        String currentUsername = userService.getCurrentUser().getUsername();
        // 1. Determine Owner
        User owner = null;
        String creatorUsername = null;
        if ("FOLDER".equalsIgnoreCase(targetType)) {
            Folder f = dataManager.load(Folder.class).id(targetId)
                .fetchPlan(fp -> {
                    fp.addAll("id", "name");
                })
                .one();
            // Infer owner from FULL permission (inherited = false)
            owner = dataManager.load(Permission.class)
                .query("select p from Permission p where p.folder = :folder and p.inherited = false and p.permissionMask = :mask")
                .parameter("folder", f)
                .parameter("mask", PermissionType.FULL.getValue())
                .fetchPlan(fp -> fp.add("user", u -> u.addAll("id", "username", "email", "firstName", "lastName")))
                .maxResults(1)
                .optional()
                .map(Permission::getUser)
                .orElse(null);
            System.out.println("Folder owner from FULL permission: " + (owner != null ? owner.getUsername() : "NULL"));
        } else {
            FileDescriptor f = dataManager.load(FileDescriptor.class).id(targetId)
                .fetchPlan(fp -> {
                    fp.addAll("id", "name", "createBy");
                })
                .one();
            creatorUsername = f.getCreateBy();
            System.out.println("File createBy: " + creatorUsername);
            // For files, prioritize createBy field to identify owner
            if (creatorUsername != null) {
                owner = dataManager.load(User.class)
                    .query("select u from User u where lower(u.username) = lower(:username)")
                    .parameter("username", creatorUsername)
                    .fetchPlan(fp -> fp.addAll("id", "username", "email", "firstName", "lastName"))
                    .optional()
                    .orElse(null);
                System.out.println("Owner from createBy: " + (owner != null ? owner.getUsername() : "NULL"));
            }
            // If no owner found from createBy, try FULL permission
            if (owner == null) {
                owner = dataManager.load(Permission.class)
                    .query("select p from Permission p where p.file = :file and p.inherited = false and p.permissionMask = :mask")
                    .parameter("file", f)
                    .parameter("mask", PermissionType.FULL.getValue())
                    .fetchPlan(fp -> fp.add("user", u -> u.addAll("id", "username", "email", "firstName", "lastName")))
                    .maxResults(1)
                    .optional()
                    .map(Permission::getUser)
                    .orElse(null);
                System.out.println("Owner from FULL permission: " + (owner != null ? owner.getUsername() : "NULL"));
            }
        }

        System.out.println("Final owner: " + (owner != null ? owner.getUsername() : "NULL"));

        // Add owner to result FIRST
        if (owner != null) {
            String fullName = (owner.getFirstName() != null ? owner.getFirstName() : "") + 
                             " " + 
                             (owner.getLastName() != null ? owner.getLastName() : "");
            fullName = fullName.trim();
            if (fullName.isEmpty()) {
                fullName = owner.getUsername();
            }
            
            result.add(new UserPermissionDto(
                owner.getId(),
                owner.getUsername(),
                owner.getEmail(),
                fullName,
                "FULL",
                true
            ));
            System.out.println("Added owner to result: " + owner.getUsername() + " (isOwner=true)");
        }

        // 2. Get Explicit & Inherited Permissions for Users
        String queryStr = "";

        if ("FOLDER".equalsIgnoreCase(targetType)) {
            queryStr = "select p from Permission p where p.folder.id = :targetId and p.user is not null";
        } else {
            queryStr = "select p from Permission p where p.file.id = :targetId and p.user is not null";
        }
        
        List<Permission> permissions = dataManager.load(Permission.class)
            .query(queryStr)
            .parameter("targetId", targetId)
            .fetchPlan(fp -> {
                fp.addAll("permissionMask", "inherited");
                fp.add("user", u -> u.addAll("id", "username", "email", "firstName", "lastName"));
            })
            .list();

        System.out.println("Found " + permissions.size() + " permission records");

        for (Permission p : permissions) {
            System.out.println("Permission: user=" + (p.getUser() != null ? p.getUser().getUsername() : "NULL") + 
                             ", mask=" + p.getPermissionMask() + 
                             ", inherited=" + p.getInherited());
            
            // Removed allow check as it is transient and likely null
            User u = p.getUser();
            if (u == null) continue;
            
            // Avoid duplicating owner
            if (owner != null && u.getId().equals(owner.getId())) {
                System.out.println("Skipping owner duplicate: " + u.getUsername());
                continue;
            }
            
            String type = PermissionType.fromId(Optional.ofNullable(p.getPermissionMask()).orElse(0)) != null ? 
                         PermissionType.fromId(Optional.ofNullable(p.getPermissionMask()).orElse(0)).name() : "READ";
            
            String fullName = (u.getFirstName() != null ? u.getFirstName() : "") + 
                             " " + 
                             (u.getLastName() != null ? u.getLastName() : "");
            fullName = fullName.trim();
            if (fullName.isEmpty()) {
                fullName = u.getUsername();
            }
            
            result.add(new UserPermissionDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                fullName,
                type,
                false
            ));
            System.out.println("Added user to result: " + u.getUsername() + " (type=" + type + ", isOwner=false)");
        }
        
        System.out.println("Total results: " + result.size());
        System.out.println("=== END DEBUG ===");
        
        return result;
    }

    private String generateUniqueToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
