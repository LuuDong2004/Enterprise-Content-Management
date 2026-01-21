package com.vn.ecm.controller;

import com.vn.ecm.dto.ShareLinkItemDto;
import com.vn.ecm.dto.UserPermissionDto;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.SharingService;
import com.vn.ecm.service.ecm.user.UserService;
import io.jmix.core.DataManager;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sharing")
public class SharingController {

    @Autowired
    private SharingService sharingService;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private UserService userService;

    // --- DTOs ---

    public static class ShareUserRequest {
        public String recipientUsername; // Or Email
        public UUID targetId;
        public String targetType; // "FOLDER" or "FILE"
        public String permissionType; // READ, MODIFY, FULL
    }

    public static class ShareLinkRequest {
        public UUID targetId;
        public String targetType;
        public String permissionType;
        public LocalDateTime expiryDate;
    }

    public static class UnshareRequest {
        public String recipientUsername;
        public UUID targetId;
        public String targetType;
    }

    public static class CheckPermissionRequest {
        public UUID targetId;
        public String targetType;
    }

    // --- API Endpoints ---

    @PostMapping("/user")
    public ResponseEntity<?> shareWithUser(@RequestBody ShareUserRequest request) {
        try {
            User owner = userService.getCurrentUser();
            User recipient = sharingService.findUserByUsernameOrEmail(request.recipientUsername)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.recipientUsername));

            Object target = loadTarget(request.targetId, request.targetType);
            PermissionType type = PermissionType.valueOf(request.permissionType);

            sharingService.shareWithUser(owner, recipient, target, type);
            return ResponseEntity.ok().body(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search-users")
    public ResponseEntity<?> searchUsers(@RequestParam String query) {
        try {
            List<User> users = sharingService.searchUsers(query);
            List<Map<String, String>> result = users.stream()
                    .map(u -> Map.of(
                            "username", u.getUsername(),
                            "email", u.getEmail() != null ? u.getEmail() : ""
                    ))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/link")
    public ResponseEntity<?> shareByLink(@RequestBody ShareLinkRequest request) {
        try {
            User owner = userService.getCurrentUser();
            Object target = loadTarget(request.targetId, request.targetType);
            PermissionType type = PermissionType.valueOf(request.permissionType);

            ShareLink link = sharingService.shareByLink(owner, target, type, request.expiryDate);
            return ResponseEntity.ok().body(link);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/user")
    public ResponseEntity<?> unshareUser(@RequestBody UnshareRequest request) {
        try {
            User owner = userService.getCurrentUser();
            User recipient = dataManager.load(User.class)
                    .query("select u from User u where u.username = :username")
                    .parameter("username", request.recipientUsername)
                    .optional()
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.recipientUsername));

            Object target = loadTarget(request.targetId, request.targetType);
            sharingService.unshareUser(owner, recipient, target);
            return ResponseEntity.ok().body(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/link/{token}")
    public ResponseEntity<?> unshareLink(@PathVariable String token) {
        try {
            User owner = userService.getCurrentUser();
            sharingService.unshareLink(owner, token);
            return ResponseEntity.ok().body(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getSharedWithMe() {
            User user = userService.getCurrentUser();
            List<ShareLinkItemDto> sharedItems = sharingService.getSharedWithMe(user);

        return ResponseEntity.ok(sharedItems.stream());
    }

    @GetMapping("/permissions")
    public ResponseEntity<?> getPermissions(@RequestParam UUID targetId, @RequestParam String targetType) {
        try {
            List<UserPermissionDto> permissions = sharingService.getPermissions(targetId, targetType);
            return ResponseEntity.ok(permissions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    @PostMapping("/check")
    public ResponseEntity<?> checkPermission(@RequestBody CheckPermissionRequest request) {
        try {
            User user = userService.getCurrentUser();
            Object target = loadTarget(request.targetId, request.targetType);
            PermissionType type = sharingService.resolvePermission(user, target);
            return ResponseEntity.ok().body(Map.of(
                "permission", type != null ? type.name() : "NONE",
                "canAccess", type != null
            ));
        } catch (Exception e) {
           return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Helpers ---

    private User loadUserByUsername(String username) {
        return dataManager.load(User.class)
            .query("select u from User u where u.username = :username")
            .parameter("username", username)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("User not found in DB: " + username));
    }

    private Object loadTarget(UUID id, String type) {
        if ("FOLDER".equalsIgnoreCase(type)) {
            return dataManager.load(Folder.class).id(id).one();
        } else if ("FILE".equalsIgnoreCase(type)) {
            return dataManager.load(FileDescriptor.class).id(id).one();
        } else {
            throw new IllegalArgumentException("Unknown target type: " + type);
        }
    }
}
