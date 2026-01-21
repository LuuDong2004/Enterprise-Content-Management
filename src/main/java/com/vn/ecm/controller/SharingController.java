package com.vn.ecm.controller;

import com.vn.ecm.dto.ShareLinkItemDto;
import com.vn.ecm.entity.*;
import com.vn.ecm.service.ecm.SharingService;
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
    private CurrentAuthentication currentAuthentication;

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
            User owner = getCurrentUser();
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
            User owner = getCurrentUser();
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
            User owner = getCurrentUser();
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
            User owner = getCurrentUser();
            sharingService.unshareLink(owner, token);
            return ResponseEntity.ok().body(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getSharedWithMe() {
            User user = getCurrentUser();
            List<ShareLinkItemDto> sharedItems = sharingService.getSharedWithMe(user);

        return ResponseEntity.ok(sharedItems.stream());
    }

    @PostMapping("/check")
    public ResponseEntity<?> checkPermission(@RequestBody CheckPermissionRequest request) {
        try {
            User user = getCurrentUser();
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
    
    private User getCurrentUser() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
             throw new IllegalStateException("No authentication found");
        }
        
        Object principal = authentication.getPrincipal();

        // 1. Check if it's already a Jmix User entity
        if (principal instanceof User) {
            return (User) principal;
        } 
        
        // 2. Check for UserDetails (Standard Spring Security)
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
             String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
             return loadUserByUsername(username);
        }
        
        // 3. Check for JWT (OAuth2 Resource Server)
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt) {
             org.springframework.security.oauth2.jwt.Jwt jwt = (org.springframework.security.oauth2.jwt.Jwt) principal;
             // Try 'preferred_username' first (Keycloak standard), then 'sub'
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
