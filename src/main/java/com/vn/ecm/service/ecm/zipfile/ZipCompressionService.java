package com.vn.ecm.service.ecm.zipfile;

import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.FileStorageLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.vn.ecm.entity.FileDescriptor;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class ZipCompressionService {
    private static final Logger log = LoggerFactory.getLogger(ZipCompressionService.class);
    private final FileStorageLocator fileStorageLocator;
    private final DynamicStorageManager dynamicStorageManager;

    public ZipCompressionService(FileStorageLocator fileStorageLocator, DynamicStorageManager dynamicStorageManager) {
        this.fileStorageLocator = fileStorageLocator;
        this.dynamicStorageManager = dynamicStorageManager;
    }

    /**
     * Nén danh sách ZipEntrySource thành một file ZIP với cấu trúc thư mục giống Windows.
     */
    public FileRef zipEntries(List<ZipEntrySourceDto> zipEntrySources,
                              String zipFileName,
                              String zipPassword) throws IOException {

        if (zipEntrySources == null || zipEntrySources.isEmpty()) {
            throw new IllegalArgumentException("Danh sách file cần nén đang rỗng.");
        }

        if (zipFileName == null || zipFileName.isBlank()) {
            zipFileName = "archive.zip";
        } else if (!zipFileName.toLowerCase().endsWith(".zip")) {
            zipFileName = zipFileName + ".zip";
        }

        File temporaryZipFile = File.createTempFile("ecm-zip-", ".zip");
        log.info("[ZipCompressionService] Start zipping {} entries into temp file {}",
                zipEntrySources.size(), temporaryZipFile.getAbsolutePath());

        try {
            boolean hasPassword = zipPassword != null && !zipPassword.isBlank();
            ZipFile zipFile = hasPassword
                    ? new ZipFile(temporaryZipFile, zipPassword.toCharArray())
                    : new ZipFile(temporaryZipFile);

            for (ZipEntrySourceDto entry : zipEntrySources) {
                FileDescriptor fileDescriptor = entry.getFileDescriptor();
                if (fileDescriptor == null || fileDescriptor.getFileRef() == null) {
                    log.warn("[ZipCompressionService] Bỏ qua entry vì FileDescriptor hoặc FileRef null: {}",
                            entry);
                    continue;
                }

                FileRef fileRef = fileDescriptor.getFileRef();
                FileStorage sourceFileStorage = resolveSourceFileStorage(fileDescriptor);

                log.info("[ZipCompressionService] Add to zip: nameInZip='{}', file='{}', storageName='{}'",
                        entry.getPath(),
                        fileDescriptor.getName(),
                        fileRef.getStorageName());

                try (InputStream fileInputStream = sourceFileStorage.openStream(fileRef)) {

                    ZipParameters parameters = new ZipParameters();
                    // path trong ZIP (ví dụ: "Folder con/Tệp 1.txt")
                    parameters.setFileNameInZip(entry.getPath());

                    if (hasPassword) {
                        parameters.setEncryptFiles(true);
                        parameters.setEncryptionMethod(EncryptionMethod.AES);
                        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                    }

                    zipFile.addStream(fileInputStream, parameters);
                }
            }

            // Lưu file ZIP vào default FileStorage (thường là "fs")
            FileStorage targetStorage = fileStorageLocator.getDefault();
            log.info("[ZipCompressionService] Save ZIP to default storageName='{}'",
                    targetStorage.getStorageName());

            try (InputStream zipInputStream = new FileInputStream(temporaryZipFile)) {
                FileRef resultRef = targetStorage.saveStream(zipFileName, zipInputStream);
                log.info("[ZipCompressionService] ZIP saved as FileRef={}", resultRef);
                return resultRef;
            }
        } finally {
            if (temporaryZipFile.exists() && !temporaryZipFile.delete()) {
                log.warn("[ZipCompressionService] Không xóa được file tạm: {}", temporaryZipFile.getAbsolutePath());
            }
        }
    }


    protected FileStorage resolveSourceFileStorage(FileDescriptor fileDescriptor) {
        FileRef fileRef = fileDescriptor.getFileRef();
        String refStorageName = fileRef.getStorageName();
        SourceStorage sourceStorage = fileDescriptor.getSourceStorage();

        // 1) Trường hợp dynamic storage (S3/FTP/WebDir, có SourceStorage)
        if (sourceStorage != null) {
            FileStorage dynamicFs = dynamicStorageManager.getOrCreateFileStorage(sourceStorage);

            // Nếu tên storage của dynamicFs khác storageName trong FileRef, log cảnh báo và fallback
            if (!refStorageName.equals(dynamicFs.getStorageName())) {
                log.warn(
                        "[ZipCompressionService] MISMATCH storageName: FileRef.storageName='{}', dynamicFs.storageName='{}', file='{}'. "
                                + "Fallback sang FileStorageLocator.getByName(FileRef.storageName).",
                        refStorageName, dynamicFs.getStorageName(), fileDescriptor.getName()
                );

                // Fallback: dùng storageName từ FileRef
                return fileStorageLocator.getByName(refStorageName);
            }

            return dynamicFs;
        }

        // 2) Trường hợp default / static storage (FileDescriptor không có SourceStorage)
        log.info("[ZipCompressionService] resolveSourceFileStorage: dùng FileStorageLocator.getByName('{}') cho file '{}'",
                refStorageName, fileDescriptor.getName());

        return fileStorageLocator.getByName(refStorageName);
    }
}
