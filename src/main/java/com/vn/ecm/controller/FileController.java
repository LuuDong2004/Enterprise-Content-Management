package com.vn.ecm.controller;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.FileType;

import com.vn.ecm.service.ecm.folderandfile.FileTypeResolver;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import com.vn.ecm.dto.FilePreviewResponseDto;
import com.vn.ecm.service.ecm.folderandfile.Impl.FileDescriptorService;
import io.jmix.core.security.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileDescriptorService fileDescriptorService;

    @Autowired
    private IFileDescriptorUploadAndDownloadService uploadAndDownloadService;


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
