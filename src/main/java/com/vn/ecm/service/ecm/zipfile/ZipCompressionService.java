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
        try {
            boolean hasPassword = zipPassword != null && !zipPassword.isBlank();
            ZipFile zipFile = hasPassword
                    ? new ZipFile(temporaryZipFile, zipPassword.toCharArray())
                    : new ZipFile(temporaryZipFile);

            for (ZipEntrySourceDto entry : zipEntrySources) {
                FileDescriptor fileDescriptor = entry.getFileDescriptor();
                if (fileDescriptor == null || fileDescriptor.getFileRef() == null) {
                    continue;
                }

                FileRef fileRef = fileDescriptor.getFileRef();
                FileStorage fileStorage = dynamicStorageManager.resolveFileStorage(fileRef);;


                try (InputStream fileInputStream = fileStorage.openStream(fileRef)) {

                    ZipParameters parameters = new ZipParameters();
                    parameters.setFileNameInZip(entry.getPath());

                    if (hasPassword) {
                        parameters.setEncryptFiles(true);
                        parameters.setEncryptionMethod(EncryptionMethod.AES);
                        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                    }

                    zipFile.addStream(fileInputStream, parameters);
                }
            }


            FileStorage targetStorage = fileStorageLocator.getDefault();
            try (InputStream zipInputStream = new FileInputStream(temporaryZipFile)) {
                FileRef resultRef = targetStorage.saveStream(zipFileName, zipInputStream);
                return resultRef;
            }
        } finally {
            if (temporaryZipFile.exists() && !temporaryZipFile.delete()) {
            }
        }
    }



}
