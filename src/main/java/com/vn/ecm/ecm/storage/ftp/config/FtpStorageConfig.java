package com.vn.ecm.ecm.storage.ftp.config;

public class FtpStorageConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String ftpPath;
    private final boolean passiveMode;

    public FtpStorageConfig(String host,
                            int port,
                            String username,
                            String password,
                            String ftpPath,
                            boolean passiveMode) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.ftpPath = (ftpPath == null || ftpPath.isBlank()) ? "/" : ftpPath;
        this.passiveMode = passiveMode;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFtpPath() {
        return ftpPath;
    }

    public boolean isPassiveMode() {
        return passiveMode;
    }
}
