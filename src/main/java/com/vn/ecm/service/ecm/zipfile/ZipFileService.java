package com.vn.ecm.service.ecm.zipfile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.FileDescriptor;
import com.vn.ecm.entity.Folder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class ZipFileService {
    @Autowired
    private ZipCompressionService zipCompressionService;


    public byte[] zipFiles(Folder parentFolder,
                           Collection<FileDescriptor> files,
                           String fileName,
                           String zipPassword) throws Exception {

        if (parentFolder == null) {
            throw new IllegalArgumentException("Thư mục không được null");
        }

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Danh sách file cần nén đang rỗng.");
        }

        if (fileName == null || fileName.isBlank()) {
            fileName = "download.zip";
        } else if (!fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        List<ZipEntrySourceDto> dtos = new ArrayList<>();
        for (FileDescriptor file : files) {
            if (file.getFileRef() == null) {
                continue;
            }
            String entryPath = file.getName();

            ZipEntrySourceDto dto = new ZipEntrySourceDto(file, entryPath);
            dtos.add(dto);
        }

        if (dtos.isEmpty()) {
            throw new IllegalStateException("Không có file hợp lệ để nén.");
        }

        return zipCompressionService.zipEntries(dtos, fileName, zipPassword);
    }
}
