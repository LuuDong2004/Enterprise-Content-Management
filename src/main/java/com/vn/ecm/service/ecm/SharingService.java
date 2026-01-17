package com.vn.ecm.service.ecm;

import com.vn.ecm.component.DriveItemTypeResolver;
import com.vn.ecm.dto.ShareLinkItemDto;
import com.vn.ecm.entity.*;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.security.CurrentAuthentication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private EntityStates entityStates;
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

        return dataManager.load(ShareLink.class)
                .query("""
            select s from ShareLink s
            where s.recipientEmail = :email
              and s.active = true
        """)
                .parameter("email", currentUser.getEmail())
                .fetchPlan(fp -> {
                    fp.add("createdDate");
                    fp.add("permissionType");
                    fp.add("expiryDate");
                    fp.add("active");

                    fp.add("owner", o -> o.add("username"));

                    fp.add("folder", f -> {
                        f.add("id");
                        f.add("name");
                    });

                    fp.add("file", f -> {
                        f.add("id");
                        f.add("name");
                    });
                })
                .list()
                .stream()
                .map(s -> {
                    boolean isFolder = s.getFolder() != null;

                    return new ShareLinkItemDto(
                            s.getId(),
                            isFolder ? s.getFolder().getName() : s.getFile().getName(),
                            isFolder ? DriveItemType.FOLDER : driveItemTypeResolver.resolve(s.getFile()),
                            s.getOwner().getUsername(),     // Shared by
                            s.getCreatedDate(),             // Shared date
                            s.getPermissionType(),
                            s.isValid()
                    );
                })
                .toList();
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
                    });
                })
                .list();

        return links.stream().map(s -> {
            boolean isFolder = s.getFolder() != null;

            return new ShareLinkItemDto(
                    s.getId(),
                    isFolder ? s.getFolder().getName() : s.getFile().getName(),
                    isFolder ? DriveItemType.FOLDER : driveItemTypeResolver.resolve(s.getFile()),
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
    
    private String generateUniqueToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
