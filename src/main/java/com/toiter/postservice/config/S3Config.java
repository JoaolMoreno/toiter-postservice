package com.toiter.postservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class S3Config {

    private static final Logger logger = LoggerFactory.getLogger(S3Config.class);

    @Value("${s3.host:}")
    private String endpoint;

    @Value("${s3.public-host:}")
    private String publicEndpoint;

    @Value("${s3.region:us-east-1}")
    private String region;

    @Value("${s3.access-key:}")
    private String accessKey;

    @Value("${s3.secret-key:}")
    private String secretKey;

    private final boolean pathStyle = Boolean.TRUE;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (pathStyle) {
            S3Configuration serviceConfig = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build();
            builder.serviceConfiguration(serviceConfig);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region));

        String presignEndpointToUse = (publicEndpoint != null && !publicEndpoint.isBlank()) ? publicEndpoint : endpoint;

        if (presignEndpointToUse != null && !presignEndpointToUse.isBlank()) {
            presignerBuilder = presignerBuilder.endpointOverride(URI.create(presignEndpointToUse));
        }

        if (pathStyle) {
            S3Configuration serviceConfig = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build();
            presignerBuilder = presignerBuilder.serviceConfiguration(serviceConfig);
        }

        S3Presigner presigner = presignerBuilder.build();
        logger.info("S3 presigner configured: endpoint='{}' (publicOverride='{}'), pathStyle={}", presignEndpointToUse, publicEndpoint, pathStyle);
        return presigner;
    }

}
