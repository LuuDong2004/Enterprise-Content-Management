package com.vn.ecm.controller;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.service.ecm.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "storageId", required = false) UUID storageId
    ) {
        try {
            FileDescriptor result = fileService.uploadFile(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getSize(),
                    folderId,
                    storageId
            );

            // Return simple DTO to avoid recursion
            Map<String, Object> response = new HashMap<>();
            response.put("id", result.getId());
            response.put("name", result.getName());
            response.put("size", result.getSize());
            response.put("extension", result.getExtension());
            response.put("createdDate", result.getLastModified()); // Using lastModified as created date equivalent
            if (result.getFolder() != null) {
                response.put("folderId", result.getFolder().getId());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
