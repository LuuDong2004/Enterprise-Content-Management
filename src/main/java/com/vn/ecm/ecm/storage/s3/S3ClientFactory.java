package com.vn.ecm.ecm.storage.s3;
//v1
import com.vn.ecm.entity.SourceStorage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;


import java.net.URI;

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
        try (S3Client s3 = create(s)) {
            if (s.getBucket() != null && !s.getBucket().isBlank()) {
                s3.headBucket(HeadBucketRequest.builder().bucket(s.getBucket()).build());
            } else {
                s3.listBuckets(ListBucketsRequest.builder().build());
            }

            long ms = Duration.between(start, Instant.now()).toMillis();
            return "Kết nối thành công (" + ms + " ms)";

        } catch (S3Exception e) {
            int sc = e.statusCode();
            if (sc == 403) return "Lỗi xác thực: sai Access Key hoặc Secret Key";
            if (sc == 404) return "Bucket không tồn tại: " + s.getBucket();
            return "Lỗi S3 (" + sc + "): " + e.getMessage();
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
}
