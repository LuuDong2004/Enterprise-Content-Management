package com.vn.ecm.controller;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;
import com.vn.ecm.entity.PermissionType;
import com.vn.ecm.entity.User;
import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.mydrive.MyDriveService;
import com.vn.ecm.service.ecm.user.UserService;
import io.jmix.core.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/drive")
public class MyDriveController {

    @Autowired
    private MyDriveService myDriveService;

    @Autowired
    private UserService userService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private DataManager dataManager;


    @GetMapping("/mydrive")
    public ResponseEntity<?> getMyDrive() {

        User currentUser = userService.getCurrentUser();

        Folder myDrive = myDriveService.getMyDriveByUser(currentUser);
        if (myDrive == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Bạn chưa được cấp My Drive"));
        }

        if (!permissionService.hasPermission(currentUser, PermissionType.READ, myDrive)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Không có quyền truy cập My Drive"));
        }

        return ResponseEntity.ok(toFolderDto(myDrive));
    }
    @GetMapping("/folders")
    public ResponseEntity<?> getSubFolders(@RequestParam UUID parentId) {

        User currentUser = userService.getCurrentUser();
        Folder parent = dataManager.load(Folder.class)
                .id(parentId)
                .optional()
                .orElse(null);

        if (parent == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Folder không tồn tại"));
        }

        List<Folder> folders = myDriveService.getSubFolders(currentUser, parent);
        return ResponseEntity.ok(
                folders.stream().map(this::toFolderDto).toList()
        );
    }
    @GetMapping("/files")
    public ResponseEntity<?> getFiles(@RequestParam UUID folderId) {

        User currentUser = userService.getCurrentUser();
        Folder folder = dataManager.load(Folder.class)
                .id(folderId)
                .optional()
                .orElse(null);

        if (folder == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Folder không tồn tại"));
        }

        List<FileDescriptor> files = myDriveService.getFiles(currentUser, folder);
        return ResponseEntity.ok(
                files.stream().map(this::toFileDto).toList()
        );
    }
    private Map<String, Object> toFolderDto(Folder f) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", f.getId().toString());
        dto.put("name", f.getName());
        dto.put("parentId", f.getParent() != null ? f.getParent().getId().toString() : null);
        dto.put("storage",
                f.getSourceStorage() != null ? f.getSourceStorage().getName() : null);
        return dto;
    }
    private Map<String, Object> toFileDto(FileDescriptor f) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", f.getId().toString());
        dto.put("name", f.getName());
        dto.put("size", f.getSize());
        dto.put("extension", f.getExtension());
        dto.put("createdDate", f.getCreateDate());
        return dto;
    }
}
