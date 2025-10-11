package com.vn.ecm.ecm.storage.web_directory;

import com.vn.ecm.entity.SourceStorage;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.nio.file.*;
import java.util.UUID;


public class WebDirectoryStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(WebDirectoryStorage.class);
    
    private final String storageName ;
    private final Path root;

    public WebDirectoryStorage(SourceStorage s) {
        this.storageName= "webdir-" + s.getId();
        if (s.getWebRootPath() == null || s.getWebRootPath().isBlank()) {
            throw new IllegalArgumentException("WEB_ROOT_PATH is required for WEBDIR");
        }
        this.root = Paths.get(s.getWebRootPath()).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            try { Files.createDirectories(root); } catch (IOException e) {
                throw new RuntimeException("Cannot create web root: " + root, e);
            }
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("Web root must be writable directory: " + root);
        }
    }

    @Override public String getStorageName() { return storageName; }

    @Override
    public FileRef saveStream(String fileName, InputStream in, Map<String, Object> params) {
        LocalDate d = LocalDate.now();
        Path subdir = root.resolve(Path.of(String.valueOf(d.getYear()),
                String.format("%02d", d.getMonthValue()),
                String.format("%02d", d.getDayOfMonth())));
        try { Files.createDirectories(subdir); } catch (IOException ignored) {}

        // Sanitize name & tạo tên duy nhất
        String sanitized = fileName;
        String unique = UUID.randomUUID() + "_" + sanitized;

        Path dest = subdir.resolve(unique).normalize();
        ensureUnderRoot(dest);

        try {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Write failed: " + dest, e);
        }
        Path rel = root.relativize(dest);
        return new FileRef(storageName, rel.toString().replace('\\','/'), sanitized);
    }

    @Override
    public InputStream openStream(FileRef ref) {
        log.info("=== OPENING FILE STREAM ===");
        log.info("FileRef: storageName={}, path={}, fileName={}", 
                ref.getStorageName(), ref.getPath(), ref.getFileName());
        
        assertSameStorage(ref);
        Path file = root.resolve(ref.getPath()).normalize();
        log.info("Resolved file path: {}", file);
        log.info("Root path: {}", root);
        
        ensureUnderRoot(file);
        
        try {
            if (!Files.exists(file)) {
                log.error("File does not exist: {}", file);
                throw new RuntimeException("File does not exist: " + file);
            }
            
            if (!Files.isRegularFile(file)) {
                log.error("Path is not a regular file: {}", file);
                throw new RuntimeException("Path is not a regular file: " + file);
            }
            
            log.info("File exists and is readable. Opening stream...");
            InputStream stream = Files.newInputStream(file, StandardOpenOption.READ);
            log.info("File stream opened successfully");
            return stream;
        } catch (IOException e) {
            log.error("Failed to open file stream: {}", file, e);
            throw new RuntimeException("Open failed: " + file, e);
        }
    }

    @Override
    public void removeFile(FileRef ref) {
        assertSameStorage(ref);
        Path file = root.resolve(ref.getPath()).normalize();
        ensureUnderRoot(file);
        try { Files.deleteIfExists(file); } catch (IOException e) {
            throw new RuntimeException("Delete failed: " + file, e);
        }
    }

    @Override
    public boolean fileExists(FileRef ref) {
        if (!storageName.equals(ref.getStorageName())) return false;
        Path file = root.resolve(ref.getPath()).normalize();
        return file.startsWith(root) && Files.exists(file);
    }

    private void assertSameStorage(FileRef ref) {
        if (!storageName.equals(ref.getStorageName())) {
            throw new IllegalArgumentException("Wrong storage: " + ref.getStorageName());
        }
    }

    private void ensureUnderRoot(Path p) {
        if (!p.toAbsolutePath().normalize().startsWith(root)) {
            throw new SecurityException("Path traversal detected: " + p);
        }
    }
}
