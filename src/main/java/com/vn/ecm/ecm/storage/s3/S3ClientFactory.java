package com.vn.ecm.ecm.storage.s3;
//v1

import com.vn.ecm.entity.SourceStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;


import javax.net.ssl.SSLHandshakeException;
import java.net.*;

import java.time.Duration;
import java.time.Instant;

@Component("ecm_S3ClientFactory")
public class S3ClientFactory {

    public S3Client create(SourceStorage sourceStorage) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
                sourceStorage.getAccessKey(), sourceStorage.getSecretAccessKey());

        S3Configuration s3cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(sourceStorage.getUsePathStyleBucketAddressing() != null && sourceStorage.getUsePathStyleBucketAddressing())
                .build();

        S3ClientBuilder build = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.builder().build())
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(5))
                        .build())
                .serviceConfiguration(s3cfg);

        if (sourceStorage.getRegion() != null && !sourceStorage.getRegion().isBlank()) {
            build = build.region(Region.of(sourceStorage.getRegion()));
        } else {
            build = build.region(Region.of("us-east-1")); // default cho MinIO
        }
        if (sourceStorage.getEndpointUrl() != null && !sourceStorage.getEndpointUrl().isBlank()) {
            build = build.endpointOverride(URI.create(sourceStorage.getEndpointUrl())); // MinIO / S3-compatible
        }
        return build.build();
    }

    public String testConnection(SourceStorage s) {
        Instant start = Instant.now();

        if (s.getName() == null || s.getName().isBlank()) {
            return "Tên kho lưu trữ không được rỗng";
        }
        if (s.getAccessKey() == null || s.getAccessKey().isBlank()) {
            return "Tài khoản không được để trống!";
        }
        if (s.getSecretAccessKey() == null || s.getSecretAccessKey().isBlank()) {
            return "Mật khẩu không được để trống!";
        }
        if(s.getBucket() == null || s.getBucket().isBlank()) {
            return "Kho lưu trữ không được rỗng";
        }
        if (s.getEndpointUrl() == null || s.getEndpointUrl().isBlank()) {
            return "Địa chỉ cuối không được để trống!";
        }
        URI endpoint;
        try {
            String raw = s.getEndpointUrl().trim();
            if (!raw.matches("(?i)^https?://.+")) {
                return "Đường dẫn phải bắt đầu bằng http:// hoặc https://";
            }
            endpoint = new URI(raw);
            if (endpoint.getHost() == null || endpoint.getHost().isBlank()) {
                return "Sai đường dẫn tới kho lưu trữ";
            }
            int port = endpoint.getPort(); // -1 nếu không chỉ định
            if (port != -1 && (port < 1 || port > 65535)) {
                return "Port vượt ngoài khoảng hợp lệ : " + port;
            }
        } catch (URISyntaxException e) {
            return "Đường dẫn không hợp lệ: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "Endpoint không hợp lệ: " + e.getMessage();
        }
        // 3) Gọi S3
        try (S3Client s3 = create(s)) {
            String bucket = s.getBucket();
            if (bucket == null || bucket.isBlank()) {
                s3.listBuckets(ListBucketsRequest.builder().build());
            } else {
                bucket = bucket.toLowerCase();
                s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            }
            long ms = Duration.between(start, Instant.now()).toMillis();
            return "Kết nối thành công (" + ms + " ms)";
        }

        catch (S3Exception e) {
            int sc = e.statusCode();
            if (sc == 403) return "Lỗi xác thực: sai tài khoản hoặc mật khẩu";
            if (sc == 404) return "Ổ lưu trữ không tồn tại: " + s.getBucket();
            return "Sai cấu hình của S3";
        }
        catch (SdkClientException e) {
            Throwable root = rootCause(e);

            if (root instanceof UnknownHostException) {
                return "Không phân giải được đường dẫn: " + endpoint.getHost();
            }
            if (root instanceof ConnectException) {
                return "Không kết nối được tới " + endpoint;
            }
            if (root instanceof SocketTimeoutException) {
                return "Kết nối tới " + endpoint + "bị lỗi";
            }
            if (root instanceof SSLHandshakeException) {
                return "Lỗi SSL  (chứng chỉ TLS không hợp lệ)";
            }
            return "Lỗi kết nối S3";
        }
        // 6) Bắt-all an toàn
        catch (Exception e) {
            return "Lỗi không xác định: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }
}
