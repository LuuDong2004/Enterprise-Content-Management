package com.vn.ecm.service.ecm.zipfile;
import com.vn.ecm.dto.ZipFileDto;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
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

/**
 * Service phục vụ view ZipPreview:
 *  - Kiểm tra file ZIP có mã hoá không
 *  - Build cây ZipFileDto cho treeDataGrid
 *  - Tải xuống nội dung 1 entry trong ZIP
 */
@Component
public class ZipPreviewService {

    private final FileStorage fileStorage;

    public ZipPreviewService(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    /**
     * Kiểm tra file ZIP có được mã hoá (có password) hay không.
     */
    public boolean isEncrypted(FileRef fileRef) {
        Path tempZip = null;
        try (InputStream in = fileStorage.openStream(fileRef)) {
            tempZip = Files.createTempFile("ecm-zip-preview-", ".zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                in.transferTo(out);
            }

            ZipFile zipFile = new ZipFile(tempZip.toFile());
            return zipFile.isEncrypted();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi đọc file ZIP: " + e.getMessage(), e);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Build cây ZipFileDto để hiển thị trên treeDataGrid.
     *
     * Quy ước:
     *  - Nếu ZIP có password nhưng password null/blank -> IllegalArgumentException("PASSWORD_REQUIRED")
     *  - Nếu password sai -> IllegalArgumentException("WRONG_PASSWORD")
     */
    public List<ZipFileDto> buildZipTree(FileRef fileRef, String password) {
        Path tempZip = null;
        ZipFile zipFile = null;

        try (InputStream in = fileStorage.openStream(fileRef)) {
            tempZip = Files.createTempFile("ecm-zip-tree-", ".zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                in.transferTo(out);
            }

            zipFile = new ZipFile(tempZip.toFile());

            if (zipFile.isEncrypted()) {
                if (password == null || password.isBlank()) {
                    throw new IllegalArgumentException("PASSWORD_REQUIRED");
                }
                zipFile.setPassword(password.toCharArray());
            }

            List<FileHeader> headers;
            try {
                headers = zipFile.getFileHeaders();
            } catch (ZipException ex) {
                // Thường là sai mật khẩu
                if (zipFile.isEncrypted()) {
                    throw new IllegalArgumentException("WRONG_PASSWORD", ex);
                }
                throw new RuntimeException("Lỗi đọc cấu trúc ZIP: " + ex.getMessage(), ex);
            }

            return buildTreeFromHeaders(headers);

        } catch (IOException e) {
            throw new RuntimeException("Lỗi đọc file ZIP từ storage: " + e.getMessage(), e);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Tải nội dung một entry trong ZIP (dùng cho nút "Tải xuống").
     *
     * @param fileRef  FileRef tới file ZIP trong storage
     * @param entryKey key trong ZIP (ví dụ: folder1/file.txt)
     * @param password mật khẩu (nếu có)
     */
    public byte[] loadEntryBytes(FileRef fileRef, String entryKey, String password) {
        Path tempZip = null;
        ZipFile zipFile = null;

        try (InputStream in = fileStorage.openStream(fileRef)) {
            tempZip = Files.createTempFile("ecm-zip-entry-", ".zip");
            try (OutputStream out = Files.newOutputStream(tempZip)) {
                in.transferTo(out);
            }

            zipFile = new ZipFile(tempZip.toFile());

            if (zipFile.isEncrypted()) {
                if (password == null || password.isBlank()) {
                    throw new IllegalArgumentException("PASSWORD_REQUIRED");
                }
                zipFile.setPassword(password.toCharArray());
            }

            FileHeader header = zipFile.getFileHeader(entryKey);
            if (header == null) {
                throw new IllegalArgumentException("Không tìm thấy entry: " + entryKey);
            }

            if (header.isDirectory()) {
                throw new IllegalArgumentException("Không thể tải xuống một thư mục.");
            }

            try (InputStream entryStream = zipFile.getInputStream(header);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                entryStream.transferTo(baos);
                return baos.toByteArray();
            } catch (ZipException ex) {
                if (zipFile.isEncrypted()) {
                    throw new IllegalArgumentException("WRONG_PASSWORD", ex);
                }
                throw new RuntimeException("Lỗi đọc entry trong ZIP: " + ex.getMessage(), ex);
            }

        } catch (IOException e) {
            throw new RuntimeException("Lỗi I/O khi xử lý ZIP: " + e.getMessage(), e);
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Chuyển danh sách FileHeader thành cây ZipFileDto (root level).
     */
    private List<ZipFileDto> buildTreeFromHeaders(List<FileHeader> headers) {
        Map<String, ZipFileDto> nodeByKey = new LinkedHashMap<>();
        List<ZipFileDto> roots = new ArrayList<>();

        for (FileHeader header : headers) {
            String path = header.getFileName(); // ví dụ: folder1/folder2/file.txt hoặc folder1/

            if (path == null || path.isEmpty()) {
                continue;
            }

            // Bỏ slash cuối nếu là thư mục
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.isEmpty()) {
                continue;
            }

            boolean isDir = header.isDirectory();
            long size = header.getUncompressedSize(); // kích thước uncompressed

            String[] parts = path.split("/");
            StringBuilder currentKey = new StringBuilder();
            ZipFileDto parent = null;

            for (int i = 0; i < parts.length; i++) {
                if (currentKey.length() > 0) {
                    currentKey.append("/");
                }
                currentKey.append(parts[i]);

                String curKeyStr = currentKey.toString();
                boolean isLastSegment = (i == parts.length - 1);

                ZipFileDto node = nodeByKey.get(curKeyStr);
                if (node == null) {
                    node = new ZipFileDto();
                    node.setName(parts[i]);
                    node.setKey(curKeyStr);

                    if (isLastSegment) {
                        node.setFolder(isDir);
                        node.setSize(isDir ? null : size);
                    } else {
                        // Các node cha trung gian luôn là thư mục
                        node.setFolder(true);
                        node.setSize(null);
                    }

                    // Gắn parent/children
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
