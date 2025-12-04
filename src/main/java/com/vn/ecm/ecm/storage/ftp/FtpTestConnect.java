package com.vn.ecm.ecm.storage.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.springframework.stereotype.Component;

@Component
public class FtpTestConnect {
    public void testConnection(String host,
                               int port,
                               String username,
                               String password,
                               boolean passiveMode) throws Exception {

        FTPSClient client = new FTPSClient("TLS");

        client.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());

        try {
            client.connect(host, port);

            int reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new Exception("Server FTP trả về mã lỗi: " + reply);
            }

            if (!client.login(username, password)) {

                throw new Exception("Không đăng nhập được vào FTP");
            }
            if (passiveMode) {
                client.enterLocalPassiveMode();
            } else {
                client.enterLocalActiveMode();
            }
            client.printWorkingDirectory();

        } catch (Exception e) {
            throw new Exception("Không thể kết nối FTP: " + e.getMessage(), e);
        } finally {
            try { client.logout(); } catch (Exception ignore) {}
            try { client.disconnect(); } catch (Exception ignore) {}
        }
    }
}
