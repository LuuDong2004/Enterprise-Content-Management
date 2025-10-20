package com.vn.ecm.ecmmodule.storage.web_directory;

import com.vn.ecm.ecmmodule.entity.SourceStorage;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.nio.file.*;
import java.util.UUID;


/**
 * FileStorage implementation cho Web Directory
 * Lưu trữ file trong thư mục local với cấu trúc thư mục theo ngày
 */
public class WebDirectoryStorage implements FileStorage {
    private final String storageName;
    private final Path rootDirectory;

    public WebDirectoryStorage(SourceStorage sourceStorage) {
        this.storageName = "webdir-" + sourceStorage.getId();
        
        if (sourceStorage.getWebRootPath() == null || sourceStorage.getWebRootPath().isBlank()) {
            throw new IllegalArgumentException("WEB_ROOT_PATH is required for WEBDIR storage");
        }
        
        this.rootDirectory = Paths.get(sourceStorage.getWebRootPath()).toAbsolutePath().normalize();
        initializeRootDirectory();
    }

    /**
     * Khởi tạo thư mục gốc, tạo nếu chưa tồn tại
     */
    private void initializeRootDirectory() {
        if (!Files.exists(rootDirectory)) {
            try {
                Files.createDirectories(rootDirectory);
            } catch (IOException e) {
                throw new RuntimeException("Không thể tạo thư mục gốc: " + rootDirectory, e);
            }
        }
        
        if (!Files.isDirectory(rootDirectory) || !Files.isWritable(rootDirectory)) {
            throw new IllegalStateException("Thư mục gốc phải là thư mục có thể ghi: " + rootDirectory);
        }
    }

    @Override public String getStorageName() { return storageName; }

    @Override
    public FileRef saveStream(String fileName, InputStream inputStream, Map<String, Object> params) {
        LocalDate currentDate = LocalDate.now();
        Path dateSubdirectory = createDateBasedSubdirectory(currentDate);
        createDirectoryIfNotExists(dateSubdirectory);

        // Sanitize filename và tạo tên duy nhất
        String sanitizedFileName = sanitizeFileName(fileName);
        String uniqueFileName = generateUniqueFileName(sanitizedFileName);

        Path targetFilePath = dateSubdirectory.resolve(uniqueFileName).normalize();
        validatePathIsUnderRoot(targetFilePath);

        try {
            Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi ghi file: " + targetFilePath, e);
        }
        
        Path relativePath = rootDirectory.relativize(targetFilePath);
        return new FileRef(storageName, relativePath.toString().replace('\\','/'), sanitizedFileName);
    }

    /**
     * Tạo thư mục con theo ngày (YYYY/MM/DD)
     */
    private Path createDateBasedSubdirectory(LocalDate date) {
        return rootDirectory.resolve(Path.of(
                String.valueOf(date.getYear()),
                String.format("%02d", date.getMonthValue()),
                String.format("%02d", date.getDayOfMonth())
        ));
    }

    /**
     * Tạo thư mục nếu chưa tồn tại
     */
    private void createDirectoryIfNotExists(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ignored) {
            // Ignore nếu thư mục đã tồn tại
        }
    }

    /**
     * Sanitize tên file để tránh ký tự đặc biệt
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Tạo tên file duy nhất với UUID prefix
     */
    private String generateUniqueFileName(String fileName) {
        return UUID.randomUUID() + "_" + fileName;
    }

    @Override
    public InputStream openStream(FileRef fileRef) {
        validateStorageMatch(fileRef);
        Path filePath = rootDirectory.resolve(fileRef.getPath()).normalize();
        validatePathIsUnderRoot(filePath);
        
        try {
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                throw new RuntimeException("File không tồn tại hoặc không phải file thông thường: " + filePath);
            }
            
            return Files.newInputStream(filePath, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi mở file: " + filePath, e);
        }
    }

    @Override
    public void removeFile(FileRef fileRef) {
        validateStorageMatch(fileRef);
        Path filePath = rootDirectory.resolve(fileRef.getPath()).normalize();
        validatePathIsUnderRoot(filePath);
        
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi xóa file: " + filePath, e);
        }
    }

    @Override
    public boolean fileExists(FileRef fileRef) {
        if (!storageName.equals(fileRef.getStorageName())) {
            return false;
        }
        
        Path filePath = rootDirectory.resolve(fileRef.getPath()).normalize();
        return filePath.startsWith(rootDirectory) && Files.exists(filePath);
    }

    /**
     * Kiểm tra FileRef có thuộc về storage này không
     */
    private void validateStorageMatch(FileRef fileRef) {
        if (!storageName.equals(fileRef.getStorageName())) {
            throw new IllegalArgumentException("FileRef không thuộc về storage này: " + fileRef.getStorageName());
        }
    }

    /**
     * Kiểm tra đường dẫn có nằm trong thư mục gốc không (bảo mật)
     */
    private void validatePathIsUnderRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(rootDirectory)) {
            throw new SecurityException("Path traversal attack detected: " + path);
        }
    }
}
