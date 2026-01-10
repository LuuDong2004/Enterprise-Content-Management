package com.vn.ecm.controller;

import com.vn.ecm.ecm.storage.DynamicStorageManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
//
@RestController
@RequestMapping("/api/excel-preview")
public class ExcelPreviewController {

    @Autowired
    private DynamicStorageManager dynamicStorageManager;

    @GetMapping(value = "/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getExcelFile(
            @RequestParam String storageName,
            @RequestParam String path,
            @RequestParam String fileName) {
        try {
            FileRef fileRef = new FileRef(storageName, path, fileName);
            FileStorage storage = dynamicStorageManager.getFileStorageByName(storageName);
            
            try (InputStream is = storage.openStream(fileRef)) {
                byte[] fileBytes = is.readAllBytes();
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                headers.setContentDispositionFormData("inline", fileName);
                headers.setContentLength(fileBytes.length);
                
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(fileBytes);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

