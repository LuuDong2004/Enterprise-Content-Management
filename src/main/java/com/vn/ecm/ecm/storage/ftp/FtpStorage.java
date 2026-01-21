package com.vn.ecm.ecm.storage.ftp;

import com.vn.ecm.ecm.storage.ftp.config.FtpStorageConfig;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.util.TrustManagerUtils;

import java.io.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime FileStorage triển khai FTPs (FileZilla explicit TLS).
 * Không phụ thuộc entity JPA, chỉ dùng FtpStorageConfig.
 */
public class FtpStorage implements FileStorage {

    private final String storageName;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String ftpPath;
    private final boolean passiveMode;

    public FtpStorage(String storageName, FtpStorageConfig cfg) {
        this.storageName = storageName;
        this.host = cfg.getHost();
        this.port = cfg.getPort();
        this.username = cfg.getUsername();
        this.password = cfg.getPassword();
        this.ftpPath = cfg.getFtpPath();
        this.passiveMode = cfg.isPassiveMode();

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
    public FileRef saveStream(String fileName,
                              InputStream inputStream,
                              Map<String, Object> parameters) {
        LocalDate today = LocalDate.now();

        String datePath = "%04d/%02d/%02d".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()
        );

        String sanitized = sanitizeFileName(fileName);
        String unique = UUID.randomUUID() + "_" + sanitized;

        String relativePath = datePath + "/" + unique;
        String remotePath = normalizeRemotePath(ftpPath, relativePath);

        FTPSClient client = createAndLoginClient();
        try (InputStream is = inputStream) {
            client.setFileType(FTP.BINARY_FILE_TYPE);

            ensureFolders(client, normalizeRemotePath(ftpPath, datePath));

            boolean ok = client.storeFile(remotePath, is);
            if (!ok) {
                int reply = client.getReplyCode();
                String msg = client.getReplyString();
                throw new RuntimeException("Không thể upload file lên FTP. ReplyCode=" +
                        reply + ", Message=" + msg);
            }

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi tải tệp lên FTP", e);
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
        FTPSClient client = createAndLoginClient();

        try {
            client.setFileType(FTP.BINARY_FILE_TYPE);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            boolean ok = client.retrieveFile(remotePath, bos);
            if (!ok) {
                int reply = client.getReplyCode();
                String msg = client.getReplyString();
                throw new FileNotFoundException("Không tìm thấy file: " + remotePath +
                        ". ReplyCode=" + reply + ", Message=" + msg);
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
        FTPSClient client = createAndLoginClient();

        try {
            boolean ok = client.deleteFile(remotePath);
            if (!ok) {
                int reply = client.getReplyCode();
                String msg = client.getReplyString();
                throw new RuntimeException("Không thể xóa file trên FTP. ReplyCode=" +
                        reply + ", Message=" + msg);
            }
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
        FTPSClient client = createAndLoginClient();

        try {
            return client.listFiles(remotePath).length == 1;
        } catch (IOException e) {
            return false;
        } finally {
            disconnect(client);
        }
    }

    // ========================= HELPER (FTPS) =========================

    private FTPSClient createAndLoginClient() {
        // Explicit FTPS (AUTH TLS) – giống FileZilla “Require explicit FTP over TLS”
        FTPSClient client = new FTPSClient("TLS");

        // Dev: chấp nhận mọi certificate (self-signed)
        client.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());

        try {
            client.connect(host, port);

            int reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new RuntimeException("Server FTP từ chối kết nối. ReplyCode=" + reply +
                        ", Message=" + client.getReplyString());
            }

            if (!client.login(username, password)) {
                throw new RuntimeException("Đăng nhập FTP thất bại (user/mật khẩu/TLS).");
            }

            // Quan trọng cho FTPS – mở TLS cho data channel
            client.execPBSZ(0);
            client.execPROT("P");

            if (passiveMode) {
                client.enterLocalPassiveMode();
            } else {
                client.enterLocalActiveMode();
            }

            return client;
        } catch (IOException e) {
            disconnect(client);
            throw new RuntimeException("Không kết nối được FTP server: " + host + ":" + port, e);
        }
    }

    private void disconnect(FTPSClient client) {
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

    private void ensureFolders(FTPSClient client, String dir) throws IOException {
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
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!rel.startsWith("/")) {
            rel = "/" + rel;
        }
        return b + rel;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
