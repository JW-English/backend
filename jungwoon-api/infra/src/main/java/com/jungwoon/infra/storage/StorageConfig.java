package com.jungwoon.infra.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    private final StorageProperties properties;

    public StorageConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();
    }
}
