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

    @Autowired
    private com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService uploadAndDownloadService;

    @Autowired
    private io.jmix.core.DataManager dataManager;

    @Autowired
    private com.vn.ecm.service.ecm.PermissionService permissionService;

    @Autowired
    private io.jmix.core.security.CurrentAuthentication currentAuthentication;

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

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable UUID id) {
        try {
            FileDescriptor fileDescriptor = dataManager.load(FileDescriptor.class)
                    .id(id)
                    .fetchPlan(fp -> {
                        fp.add("name");
                        fp.add("extension");
                        fp.add("fileRef");
                        fp.add("sourceStorage");
                    })
                    .optional()
                    .orElseThrow(() -> new IllegalArgumentException("File not found: " + id));

            // Logic check quyền có thể thêm ở đây nếu cần
            
            byte[] fileBytes = uploadAndDownloadService.downloadFile(fileDescriptor);
            
            String contentType = determineContentType(fileDescriptor.getExtension());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileDescriptor.getName() + "\"")
                    .body(fileBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String determineContentType(String extension) {
        if (extension == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        switch (extension.toLowerCase()) {
            case "pdf": return MediaType.APPLICATION_PDF_VALUE;
            case "jpg":
            case "jpeg": return MediaType.IMAGE_JPEG_VALUE;
            case "png": return MediaType.IMAGE_PNG_VALUE;
            case "gif": return MediaType.IMAGE_GIF_VALUE;
            case "txt": return MediaType.TEXT_PLAIN_VALUE;
            case "html": return MediaType.TEXT_HTML_VALUE;
            case "xml": return MediaType.APPLICATION_XML_VALUE;
            case "json": return MediaType.APPLICATION_JSON_VALUE;
            default: return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
