package com.vn.ecm.ecm.storage.s3;

import com.vn.ecm.entity.SourceStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

public class S3ClientFactory {

    public S3Client create(SourceStorage s) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(
                s.getAccessKey(), s.getSecretAccessKey());

        S3Configuration s3cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(s.getUsePathStyleBucketAddressing() != null && s.getUsePathStyleBucketAddressing())
                .build();

        S3ClientBuilder b = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.builder().build())
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(5))
                        .build())
                .serviceConfiguration(s3cfg);

        if (s.getRegion() != null && !s.getRegion().isBlank()) {
            b = b.region(Region.of(s.getRegion()));
        }
        if (s.getEndpointUrl() != null && !s.getEndpointUrl().isBlank()) {
            b = b.endpointOverride(URI.create(s.getEndpointUrl())); // MinIO / S3-compatible
        }
        return b.build();
    }
}
