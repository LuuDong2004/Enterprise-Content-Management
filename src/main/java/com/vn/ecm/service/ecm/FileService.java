package com.vn.ecm.service.ecm;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.*;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.security.CurrentAuthentication;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service("ecm_FileService")
public class FileService {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentAuthentication currentAuthentication;

    @Transactional
    public FileDescriptor uploadFile(InputStream stream, String originalFileName, long size, UUID folderId, UUID storageId) {
        User currentUser = getCurrentUser();
        // 1. Determine Target Folder & Storage
        Folder folder = null;
        SourceStorage sourceStorage = null;

        if (folderId != null) {
            folder = dataManager.load(Folder.class).id(folderId).one();
            // Inherit storage from folder
            sourceStorage = folder.getSourceStorage();
        }

        // If storage not determined by folder (e.g. root upload), use passed storageId
        if (sourceStorage == null && storageId != null) {
            sourceStorage = dataManager.load(SourceStorage.class).id(storageId).one();
        }

        // If still null, try to find a default storage (optional logic)
        if (sourceStorage == null) {
             sourceStorage = findDefaultStorage();
             if (sourceStorage == null) {
                 throw new IllegalArgumentException("No storage specified and no default storage found.");
             }
        }

        // 2. Validate Permission (Create in Folder)
        if (folder != null) {
             if (!permissionService.hasPermission(currentUser, PermissionType.CREATE, folder)) {
                 throw new SecurityException("No permission to upload files to this folder.");
             }
        } else {
            // Root level: usually check generic "CREATE" on Storage? or System role?
            // Assuming allowed for now or check storage permission if applicable
        }

        // 3. Save physical file
        FileStorage fileStorage = dynamicStorageManager.getOrCreateFileStorage(sourceStorage);
        FileRef fileRef;
        try {
            fileRef = fileStorage.saveStream(originalFileName, stream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save file to storage: " + e.getMessage(), e);
        }

        // 4. Create Entity
        FileDescriptor fileDescriptor = dataManager.create(FileDescriptor.class);
        fileDescriptor.setName(originalFileName);
        fileDescriptor.setExtension(FilenameUtils.getExtension(originalFileName));
        fileDescriptor.setSize(size);
        fileDescriptor.setLastModified(LocalDateTime.now());
        fileDescriptor.setFolder(folder);
        fileDescriptor.setSourceStorage(sourceStorage);
        fileDescriptor.setFileRef(fileRef);
        fileDescriptor.setCreateBy(currentUser.getUsername());
        fileDescriptor.setInTrash(false);

        FileDescriptor savedFile = dataManager.save(fileDescriptor);

        // 5. Initialize Permissions (Owner permissions)
        permissionService.initializeFilePermission(currentUser, savedFile);

        return savedFile;
    }

    private SourceStorage findDefaultStorage() {
        // Simple logic: find first active storage
        return dataManager.load(SourceStorage.class)
                .query("select s from SourceStorage s where s.active = true")
                .maxResults(1)
                .optional()
                .orElse(null);
    }

    private User getCurrentUser() {
        Object principal = currentAuthentication.getUser();
        if (principal instanceof User) {
            return (User) principal;
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
             String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
             return dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .one();
        } else {
            // Check if it is Jwt token
            if (principal instanceof org.springframework.security.oauth2.jwt.Jwt) {
                 String username = ((org.springframework.security.oauth2.jwt.Jwt) principal).getClaimAsString("preferred_username");
                 if (username == null) username = ((org.springframework.security.oauth2.jwt.Jwt) principal).getSubject();
                 
                  return dataManager.load(User.class)
                    .query("select u from User u where u.username = :username")
                    .parameter("username", username)
                    .one();
            }
            throw new IllegalStateException("Unknown principal type: " + principal.getClass());
        }
    }
}
