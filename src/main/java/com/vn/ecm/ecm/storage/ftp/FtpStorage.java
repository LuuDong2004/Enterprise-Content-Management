package com.vn.ecm.ecm.storage.ftp;

import com.vn.ecm.entity.FtpStorageEntity;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime FileStorage triển khai FTP (FileZilla)
 * KHÁC với entity com.vn.ecm.entity.FtpStorageEntity
 */
public class FtpStorage implements FileStorage {

    private final String storageName;

    private final String host;
    private final Integer port;
    private final String username;
    private final String password;
    private final String ftpPath;
    private final boolean passiveMode;

    public FtpStorage(FtpStorageEntity ftpStorage) {
        this.storageName = "ftp-" + ftpStorage.getId();

        this.host = ftpStorage.getHost();
        this.port = Integer.valueOf(ftpStorage.getPort());
        this.username = ftpStorage.getUsername();
        this.password = ftpStorage.getPassword();
        this.ftpPath = ftpStorage.getFtpPath() != null ? ftpStorage.getFtpPath() : "/";
        this.passiveMode = Boolean.TRUE.equals(ftpStorage.getPassiveMode());

        if (host == null || host.isBlank()
                || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Thiếu thông số kết nối FTP!");
        }
    }

    @Override
    public String getStorageName() {
        return storageName;
    }

    // ========================= SAVE =========================

    @Override
    public FileRef saveStream(String fileName, InputStream inputStream) {
        return saveStream(fileName, inputStream, Map.of());
    }

    @Override
    public FileRef saveStream(String fileName, InputStream inputStream, Map<String, Object> parameters) {
        LocalDate today = LocalDate.now();

        String datePath = "%04d/%02d/%02d".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()
        );

        String sanitized = sanitizeFileName(fileName);
        String unique = UUID.randomUUID() + "_" + sanitized;

        String relativePath = datePath + "/" + unique;
        String remotePath = normalizeRemotePath(ftpPath, relativePath);

        FTPClient client = createAndLoginClient();
        try (InputStream is = inputStream) {
            client.setFileType(FTP.BINARY_FILE_TYPE);

            ensureFolders(client, normalizeRemotePath(ftpPath, datePath));

            boolean ok = client.storeFile(remotePath, is);
            if (!ok) {
                throw new RuntimeException("Không thể upload file lên FTP: " + client.getReplyString());
            }

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi ghi file lên FTP", e);
        } finally {
            disconnect(client);
        }

        return new FileRef(storageName, relativePath, sanitized);
    }

    // ========================= OPEN =========================

    @Override
    public InputStream openStream(FileRef reference) {
        if (!storageName.equals(reference.getStorageName())) {
            throw new IllegalArgumentException("FileRef không thuộc storage này!");
        }

        String remotePath = normalizeRemotePath(ftpPath, reference.getPath());
        FTPClient client = createAndLoginClient();

        try {
            client.setFileType(FTP.BINARY_FILE_TYPE);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            boolean ok = client.retrieveFile(remotePath, bos);
            if (!ok) {
                throw new FileNotFoundException("Không tìm thấy file: " + remotePath);
            }

            return new ByteArrayInputStream(bos.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi tải file FTP", e);
        } finally {
            disconnect(client);
        }
    }

    // ========================= DELETE =========================

    @Override
    public void removeFile(FileRef reference) {
        if (!storageName.equals(reference.getStorageName())) {
            throw new IllegalArgumentException("FileRef không thuộc FTP storage này");
        }

        String remotePath = normalizeRemotePath(ftpPath, reference.getPath());
        FTPClient client = createAndLoginClient();

        try {
            client.deleteFile(remotePath);
        } catch (IOException e) {
            throw new RuntimeException("Không thể xóa file trên FTP: " + remotePath, e);
        } finally {
            disconnect(client);
        }
    }

    // ========================= EXISTS =========================

    @Override
    public boolean fileExists(FileRef reference) {
        if (!storageName.equals(reference.getStorageName())) {
            return false;
        }

        String remotePath = normalizeRemotePath(ftpPath, reference.getPath());
        FTPClient client = createAndLoginClient();

        try {
            return client.listFiles(remotePath).length == 1;
        } catch (IOException e) {
            return false;
        } finally {
            disconnect(client);
        }
    }

    // ========================= HELPER =========================

    private FTPClient createAndLoginClient() {
        FTPClient client = new FTPClient();
        try {
            client.connect(host, port);
            if (!client.login(username, password)) {
                throw new RuntimeException("Login FTP thất bại!");
            }

            if (passiveMode) {
                client.enterLocalPassiveMode();
            }

            return client;

        } catch (IOException e) {
            disconnect(client);
            throw new RuntimeException("Không kết nối được FTP server: " + host + ":" + port, e);
        }
    }

    private void disconnect(FTPClient client) {
        try {
            if (client != null && client.isConnected()) {
                client.logout();
            }
        } catch (Exception ignore) {
        }
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    private void ensureFolders(FTPClient client, String dir) throws IOException {
        String[] parts = dir.split("/");
        String current = "";

        for (String p : parts) {
            if (p.isBlank()) continue;
            current += "/" + p;
            client.makeDirectory(current);
        }
    }

    private String normalizeRemotePath(String base, String rel) {
        String b = base == null ? "" : base.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (!rel.startsWith("/")) rel = "/" + rel;
        return b + rel;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
