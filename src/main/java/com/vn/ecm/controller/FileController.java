package com.vn.ecm.controller;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.*;

import com.vn.ecm.service.ecm.PermissionService;
import com.vn.ecm.service.ecm.folderandfile.FileTypeResolver;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import com.vn.ecm.dto.FilePreviewResponseDto;
import com.vn.ecm.service.ecm.folderandfile.Impl.FileDescriptorService;
import io.jmix.core.DataManager;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.upload.TemporaryStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileDescriptorService fileDescriptorService;

    @Autowired
    private IFileDescriptorUploadAndDownloadService uploadAndDownloadService;
    @Autowired
    private TemporaryStorage temporaryStorage;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private DynamicStorageManager dynamicStorageManager;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private PermissionService permissionService;

    //api preview
    @GetMapping("/{id}/preview")
    public ResponseEntity<FilePreviewResponseDto> preview(@PathVariable UUID id) {

        FileDescriptor fd = fileDescriptorService.loadFile(id);

        FileType fileType = FileTypeResolver.resolve(fd.getExtension());

        FilePreviewResponseDto response = new FilePreviewResponseDto();
        response.setFileName(fd.getName());
        response.setFileType(fileType.name());

        if (isDirectPreview(fileType)) {
            response.setPreviewType("DIRECT");
            response.setPreviewUrl("/api/files/" + id + "/download");
        }
        else if (fileType == FileType.OFFICE) {

            String fileUrl = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/api/files/{id}/download")
                    .buildAndExpand(id)
                    .toUriString();

            String googleViewerUrl =
                    "https://docs.google.com/gview?url=" +
                            URLEncoder.encode(fileUrl, StandardCharsets.UTF_8) +
                            "&embedded=true";

            response.setPreviewType("GOOGLE_VIEWER");
            response.setPreviewUrl(googleViewerUrl);
        }
        else {
            response.setPreviewType("NOT_SUPPORTED");
        }

        return ResponseEntity.ok(response);
    }
    //api download
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable UUID id) {
        try {
            FileDescriptor fd = fileDescriptorService.loadFile(id);
            byte[] data = uploadAndDownloadService.downloadFile(fd);

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            FileType fileType = FileTypeResolver.resolve(fd.getExtension());

            if (fileType == FileType.IMAGE) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (fileType == FileType.PDF) {
                mediaType = MediaType.APPLICATION_PDF;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fd.getName() + "\""
                    )
                    .header(
                            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION
                    )
                    .contentLength(data.length)
                    .body(data);


        } catch (AccessDeniedException ex) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "ACCESS_DENIED");
            body.put("message", "You do not have permission to access this file");

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(body);
        }
    }


    //api uppload file
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folderId") UUID folderId,
            @RequestParam("sourceStorageId") UUID sourceStorageId,
            @RequestParam("username") String username
    ) {

        UUID tempFileId = null;

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Tệp bị rỗng "));
            }

            Folder folder = dataManager.load(Folder.class)
                    .id(folderId)
                    .optional()
                    .orElseThrow(() ->
                            new IllegalArgumentException("Không tìm thấy thư mục: " + folderId));

            SourceStorage sourceStorage = dataManager.load(SourceStorage.class)
                    .id(sourceStorageId)
                    .optional()
                    .orElseThrow(() ->
                            new IllegalArgumentException("Kho lưu trữ không tồn tại: " + sourceStorageId));


            if (!dynamicStorageManager.isStorageValid(sourceStorage)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Kho chưa được kích hoạt "));
            }


            try (InputStream is = file.getInputStream()) {
                tempFileId = temporaryStorage.saveFile(is, null);
            }

            File tempFile = temporaryStorage.getFile(tempFileId);
            if (tempFile == null || !tempFile.exists()) {
                throw new IllegalStateException("Temporary file not found");
            }

            // Đăng ký kho động
            dynamicStorageManager.getOrCreateFileStorage(sourceStorage);

            FileDescriptor saved = uploadAndDownloadService.uploadFile(
                    tempFileId,
                    file.getOriginalFilename(),
                    file.getSize(),
                    folder,
                    sourceStorage,
                    username,
                    tempFile
            );
            User user = (User) currentAuthentication.getUser();

            // gán quyền của user cho file đó
            permissionService.initializeFilePermission(user, saved);

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "name", saved.getName(),
                    "extension", saved.getExtension(),
                    "size", saved.getSize(),
                    "folderId", folder.getId(),
                    "sourceStorageId", sourceStorage.getId()
            ));

        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "status", 403,
                            "error", "Bạn không có quyền tải lên hệ thống!"
                    ));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", 500,
                            "error", "Tải lên thất bại",
                            "message", ex.getMessage()
                    ));
        }
    }
    private boolean isDirectPreview(FileType type) {
        return type == FileType.PDF
                || type == FileType.IMAGE
                || type == FileType.VIDEO
                || type == FileType.AUDIO
                || type == FileType.TEXT
                || type == FileType.HTML
                || type == FileType.CODE;
    }

}
