package com.vn.ecm.service.ecm.zipfile;


import com.vn.ecm.dto.ZipEntrySourceDto;
import com.vn.ecm.entity.FileDescriptor;


import com.vn.ecm.service.ecm.folderandfile.IFileDescriptorUploadAndDownloadService;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;



@Service
public class ZipCompressionService {
    @Autowired
    private IFileDescriptorUploadAndDownloadService downloadService;


    /**
     * Nén danh sách entry thành 1 file ZIP, trả về mảng byte để Download.
     * Hiện tại chưa hỗ trợ password (zipPassword đang bỏ qua).
     */
    public byte[] zipEntries(List<ZipEntrySourceDto> sources,
                             String zipFileName,
                             String password) throws Exception {

        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Danh sách file cần nén đang rỗng.");
        }

        Path tempZipPath = Files.createTempFile("ecm-zip-", ".zip");


        boolean usePassword = password != null && !password.isBlank();

        ZipFile zipFile = usePassword
                ? new ZipFile(tempZipPath.toFile(), password.toCharArray())
                : new ZipFile(tempZipPath.toFile());

        for (ZipEntrySourceDto dto : sources) {
            FileDescriptor fd = dto.getFileDescriptor();
            String entryPath = dto.getPath();

            if (fd == null || fd.getId() == null) {
                continue;
            }
            if (entryPath == null || entryPath.isBlank()) {
                continue;
            }

            try {

                byte[] fileBytes = downloadService.downloadFile(fd);
                if (fileBytes == null || fileBytes.length == 0) {
                    continue;
                }

                ZipParameters params = new ZipParameters();
                params.setFileNameInZip(entryPath);

                if (usePassword) {
                    params.setEncryptFiles(true);
                    params.setEncryptionMethod(EncryptionMethod.ZIP_STANDARD);
                }

                try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes)) {
                    zipFile.addStream(bais, params);
                }

            } catch (ZipException ze) {
                throw ze;
            } catch (Exception ex) {
                throw ex;
            }
        }

        byte[] zipBytes = Files.readAllBytes(tempZipPath);

        try {
            Files.deleteIfExists(tempZipPath);
        } catch (Exception ignore) {
        }

        return zipBytes;
    }


}
