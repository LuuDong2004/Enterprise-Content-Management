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
        }
        if (sourceStorage.getEndpointUrl() != null && !sourceStorage.getEndpointUrl().isBlank()) {
            build = build.endpointOverride(URI.create(sourceStorage.getEndpointUrl())); // MinIO / S3-compatible
        }
        return build.build();
    }
}
