package com.vn.ecm.controller;

import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.FileType;

import com.vn.ecm.service.ecm.folderandfile.FileTypeResolver;
import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import com.vn.ecm.dto.FilePreviewResponseDto;
import com.vn.ecm.service.ecm.folderandfile.Impl.FileDescriptorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileDescriptorService fileAccessService;

    @Autowired
    private IFileDescriptorUploadAndDownloadService uploadAndDownloadService;


    @GetMapping("/{id}/preview")
    public ResponseEntity<FilePreviewResponseDto> preview(@PathVariable UUID id) {

        FileDescriptor fd = fileAccessService.loadFile(id);

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
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {

        FileDescriptor fd = fileAccessService.loadFile(id);

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
                        "inline; filename=\"" + fd.getName() + "\""
                )
                .body(data);
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
