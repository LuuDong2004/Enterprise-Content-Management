package com.vn.ecm.service.ecm.zipfile;

import com.vn.ecm.dto.ZipFileDto;
import com.vn.ecm.ecm.storage.DynamicStorageManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class ZipPreviewService {

    protected final FileStorageLocator fileStorageLocator;
    protected final DynamicStorageManager dynamicStorageManager;

    public ZipPreviewService(FileStorageLocator fileStorageLocator,
                             DynamicStorageManager dynamicStorageManager) {
        this.fileStorageLocator = fileStorageLocator;
        this.dynamicStorageManager = dynamicStorageManager;
    }

    /**
     * Lấy đúng FileStorage:
     * - Kho động (s3-uuid, webdir-uuid, ftp-uuid) -> DynamicStorageManager
     * - Khác -> kho mặc định (localfs / dbfs)
     */
    protected FileStorage getFileStorage(FileRef fileRef) {
        String storageName = fileRef.getStorageName();

        if (storageName != null && !storageName.isBlank()) {
            try {
                return dynamicStorageManager.getFileStorageByName(storageName);
            } catch (Exception ignored) {
                // Không tìm thấy trong kho động -> fallback
            }
        }
        return fileStorageLocator.getDefault();
    }

    // =====================================================
    // Build cây ZIP – DỰA VÀO ZipException để check password
    // =====================================================
    public List<ZipFileDto> buildZipTree(FileRef fileRef, String password) throws Exception {
        Path tempZip = null;
        FileStorage storage = getFileStorage(fileRef);

        try (InputStream in = storage.openStream(fileRef)) {
            tempZip = Files.createTempFile("ecm-zip-tree-", ".zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                in.transferTo(out);
            }

            ZipFile zipFile = new ZipFile(tempZip.toFile());

            // Nếu người dùng đã nhập mật khẩu -> set password
            if (password != null && !password.isBlank()) {
                zipFile.setPassword(password.toCharArray());
            }

            List<FileHeader> headers;
            try {
                headers = zipFile.getFileHeaders();
            } catch (ZipException ze) {
                // Thiếu hoặc sai mật khẩu -> để controller quyết định (mở popup / báo sai pass)
                throw ze;
            }

            // 🔥 Quan trọng: kiểm tra password thực sự
            FileHeader testHeader = headers.stream()
                    .filter(h -> !h.isDirectory())
                    .findFirst()
                    .orElse(null);

            if (testHeader != null) {
                try (InputStream entryStream = zipFile.getInputStream(testHeader)) {
                    // Đọc 1–2 byte để ép Zip4j decrypt
                    byte[] buf = new byte[2];
                    // không cần quan tâm kết quả, chỉ cần nếu sai pass sẽ ném ZipException
                    entryStream.read(buf);
                } catch (ZipException ze) {
                    // Sai hoặc thiếu password
                    throw ze;
                }
            }
            // Nếu tới đây mà không có ZipException:
            // -> hoặc không cần password, hoặc password đúng.

            return buildTreeFromHeaders(headers);

        } catch (ZipException ze) {
            throw ze;
        } catch (IOException ioe) {
            throw new Exception("Lỗi I/O khi đọc file ZIP: " + ioe.getMessage(), ioe);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
    }

    // =====================================================
    // Đọc bytes 1 entry để download – cũng check password
    // =====================================================
    public byte[] loadEntryBytes(FileRef fileRef, String entryKey, String password) throws Exception {
        Path tempZip = null;
        FileStorage storage = getFileStorage(fileRef);

        try (InputStream in = storage.openStream(fileRef)) {
            tempZip = Files.createTempFile("ecm-zip-entry-", ".zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                in.transferTo(out);
            }

            ZipFile zipFile = new ZipFile(tempZip.toFile());

            if (password != null && !password.isBlank()) {
                zipFile.setPassword(password.toCharArray());
            }

            FileHeader header = zipFile.getFileHeader(entryKey);
            if (header == null) {
                throw new Exception("Không tìm thấy entry: " + entryKey);
            }
            if (header.isDirectory()) {
                throw new Exception("Không thể tải xuống thư mục.");
            }

            try (InputStream entryStream = zipFile.getInputStream(header);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                // Nếu password thiếu/sai, Zip4j ném ZipException ở đây
                entryStream.transferTo(baos);
                return baos.toByteArray();
            }

        } catch (ZipException ze) {
            // Thiếu hoặc sai password
            throw ze;
        } catch (IOException ioe) {
            throw new Exception("Lỗi I/O khi đọc entry ZIP: " + ioe.getMessage(), ioe);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
    }

    // =====================================================
    // Build cây DTO từ FileHeader
    // =====================================================
    protected List<ZipFileDto> buildTreeFromHeaders(List<FileHeader> headers) {
        Map<String, ZipFileDto> nodeByKey = new LinkedHashMap<>();
        List<ZipFileDto> roots = new ArrayList<>();

        for (FileHeader header : headers) {
            String path = header.getFileName();
            if (path == null || path.isEmpty()) {
                continue;
            }

            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.isEmpty()) {
                continue;
            }

            boolean isDir = header.isDirectory();
            long size = header.getUncompressedSize();

            String[] parts = path.split("/");
            StringBuilder currentKey = new StringBuilder();
            ZipFileDto parent = null;

            for (int i = 0; i < parts.length; i++) {
                if (currentKey.length() > 0) {
                    currentKey.append("/");
                }
                currentKey.append(parts[i]);

                String curKeyStr = currentKey.toString();
                boolean isLast = (i == parts.length - 1);

                ZipFileDto node = nodeByKey.get(curKeyStr);
                if (node == null) {
                    node = new ZipFileDto(); // id tự sinh trong DTO
                    node.setName(parts[i]);
                    node.setKey(curKeyStr);

                    if (isLast) {
                        node.setFolder(isDir);
                        node.setSize(isDir ? null : size);
                    } else {
                        node.setFolder(Boolean.TRUE);
                        node.setSize(null);
                    }

                    node.setParent(parent);
                    nodeByKey.put(curKeyStr, node);

                    if (parent == null) {
                        roots.add(node);
                    } else {
                        parent.getChildren().add(node);
                    }
                }

                parent = node;
            }
        }

        return roots;
    }
}
