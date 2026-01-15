package com.vn.ecm.controller;

import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.folderandfile.IFolderService;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    @Autowired
    private DataManager dataManager;

    @Autowired
    private IFolderService folderService;

    @Autowired
    private PermissionService permissionService;

    // Helper to get current user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext().getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException("No authentication found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return (User) principal;
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            return loadUserByUsername(username);
        }

        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt) {
            Jwt jwt = (Jwt) principal;
            String username = jwt.getClaimAsString("preferred_username");
            if (username == null) {
                username = jwt.getSubject();
            }
            return loadUserByUsername(username);
        }

        throw new IllegalStateException("Unknown principal type: " + principal.getClass().getName());
    }

    private User loadUserByUsername(String username) {
        return dataManager.load(User.class)
                .query("select u from User u where u.username = :username")
                .parameter("username", username)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("User not found in DB: " + username));
    }

    // GET /api/folders/root - Get root folder
    @GetMapping("/root")
    public ResponseEntity<?> getRootFolder() {
        try {

            // Find a root folder (parent is null) - get first one or find by user's default
            // storage
            List<Folder> rootFolders = dataManager.load(Folder.class)
                    .query("select f from Folder f where f.parent is null and f.inTrash = false")
                    .list();

            if (rootFolders.isEmpty()) {
                // No root folder exists, could return 404 or create one
                return ResponseEntity.notFound().build();
            }

            // For now, return the first root folder
            // In a multi-storage scenario, you might want to filter by user's default
            // storage
            Folder rootFolder = rootFolders.get(0);

            // Convert to DTO
            Map<String, Object> folderDto = toFolderDto(rootFolder);

            return ResponseEntity.ok(folderDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/folders/{id} - Get folder contents (subfolders and files)
    @GetMapping("/{id}")
    public ResponseEntity<?> getFolderContents(@PathVariable UUID id) {
        try {
            User user = getCurrentUser();
            Folder folder = dataManager.load(Folder.class).id(id).optional()
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + id));

            // Check permission - Allow access to root folders (parent is null) or if user
            // has READ permission
            boolean isRootFolder = folder.getParent() == null;
            if (!isRootFolder && !permissionService.hasPermission(user, PermissionType.READ, folder)) {
                return ResponseEntity.status(403).body(Map.of("error", "No permission to access this folder"));
            }

            // Get subfolders
            List<Folder> allSubfolders = dataManager.load(Folder.class)
                    .query("select f from Folder f where f.parent = :parent and f.inTrash = false")
                    .parameter("parent", folder)
                    .list();

            // Get files
            List<FileDescriptor> allFiles = dataManager.load(FileDescriptor.class)
                    .query("select f from FileDescriptor f where f.folder = :folder and f.inTrash = false")
                    .parameter("folder", folder)
                    .list();

            // Filter subfolders and files by permission
            // For root folder, allow all subfolders/files (owner should have permission
            // anyway)
            // For other folders, filter by permission
            List<Folder> subfolders;
            List<FileDescriptor> files;

            if (isRootFolder) {
                // Root folder: show all subfolders and files (permission already checked at
                // folder level)
                subfolders = allSubfolders;
                files = allFiles;
            } else {
                // Non-root folder: filter by permission
                subfolders = allSubfolders.stream()
                        .filter(subfolder -> permissionService.hasPermission(user, PermissionType.READ, subfolder))
                        .collect(Collectors.toList());

                files = allFiles.stream()
                        .filter(file -> permissionService.hasPermission(user, PermissionType.READ, file))
                        .collect(Collectors.toList());
            }

            // Build response DTO
            Map<String, Object> response = new HashMap<>();
            response.put("folder", toFolderDto(folder));
            response.put("subfolders", subfolders.stream()
                    .map(this::toFolderDto)
                    .collect(Collectors.toList()));
            response.put("files", files.stream()
                    .map(this::toFileDto)
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/folders/create - Create a new folder
    @PostMapping("/create")
    public ResponseEntity<?> createFolder(@RequestBody CreateFolderRequest request) {
        try {
            User user = getCurrentUser();

            Folder parent = null;
            if (request.parentId != null) {
                parent = dataManager.load(Folder.class).id(request.parentId).optional()
                        .orElseThrow(
                                () -> new IllegalArgumentException("Parent folder not found: " + request.parentId));

                // Check permission
                if (!permissionService.hasPermission(user, PermissionType.CREATE, parent)) {
                    return ResponseEntity.status(403).body(Map.of("error", "No permission to create folder here"));
                }
            }

            // Create folder entity
            Folder newFolder = dataManager.create(Folder.class);
            newFolder.setName(request.name);
            newFolder.setParent(parent);

            // Set source storage - inherit from parent or use default
            SourceStorage sourceStorage = null;
            if (parent != null) {
                sourceStorage = parent.getSourceStorage();
            } else {
                // For root folder, find default storage
                sourceStorage = dataManager.load(SourceStorage.class)
                        .query("select s from SourceStorage s where s.active = true")
                        .maxResults(1)
                        .optional()
                        .orElse(null);
            }
            newFolder.setSourceStorage(sourceStorage);

            // Save using folder service
            Folder savedFolder = folderService.createFolder(newFolder);

            return ResponseEntity.ok(toFolderDto(savedFolder));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE /api/folders/{id} - Delete a folder
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFolder(@PathVariable UUID id) {
        try {
            User user = getCurrentUser();
            Folder folder = dataManager.load(Folder.class).id(id).optional()
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + id));

            // Check permission (MODIFY or FULL required to delete)
            if (!permissionService.hasPermission(user, PermissionType.MODIFY, folder)
                    && !permissionService.hasPermission(user, PermissionType.FULL, folder)) {
                return ResponseEntity.status(403).body(Map.of("error", "No permission to delete this folder"));
            }

            // Move to trash
            folderService.moveToTrash(folder, user.getUsername());

            return ResponseEntity.ok(Map.of("message", "Folder deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/folders/{id}/path - Get folder path
    @GetMapping("/{id}/path")
    public ResponseEntity<?> getFolderPath(@PathVariable UUID id) {
        try {
            Folder folder = dataManager.load(Folder.class).id(id).optional()
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + id));

            // Build path by traversing parent chain
            List<Map<String, Object>> path = new ArrayList<>();
            Folder current = folder;
            while (current != null) {
                Map<String, Object> folderDto = toFolderDto(current);
                path.add(0, folderDto); // Add to beginning to reverse order
                current = current.getParent();
            }

            return ResponseEntity.ok(path);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // DTO classes
    public static class CreateFolderRequest {
        public String name;
        public UUID parentId;
    }

    // Helper methods to convert entities to DTOs
    private Map<String, Object> toFolderDto(Folder folder) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", folder.getId().toString());
        dto.put("name", folder.getName());
        dto.put("createdDate", folder.getCreatedDate() != null ? folder.getCreatedDate().toString() : null);
        if (folder.getParent() != null) {
            Map<String, Object> parentDto = new HashMap<>();
            parentDto.put("id", folder.getParent().getId().toString());
            parentDto.put("name", folder.getParent().getName());
            dto.put("parent", parentDto);
        }
        return dto;
    }

    private Map<String, Object> toFileDto(FileDescriptor file) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", file.getId().toString());
        dto.put("name", file.getName());
        dto.put("extension", file.getExtension());
        dto.put("size", file.getSize());
        dto.put("lastModified", file.getLastModified() != null ? file.getLastModified().toString() : null);
        if (file.getFolder() != null) {
            Map<String, Object> folderDto = new HashMap<>();
            folderDto.put("id", file.getFolder().getId().toString());
            folderDto.put("name", file.getFolder().getName());
            dto.put("folder", folderDto);
        }
        return dto;
    }
}
