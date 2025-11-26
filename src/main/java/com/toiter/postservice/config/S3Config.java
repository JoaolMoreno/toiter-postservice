package com.toiter.postservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${s3.host}")
    private String s3Host;

    @Value("${s3.region}")
    private String s3Region;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        
        software.amazon.awssdk.services.s3.S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        
        if (s3Host != null && !s3Host.isEmpty()) {
            builder.endpointOverride(URI.create(s3Host));
        }
        
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        
        software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(s3Region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        
        if (s3Host != null && !s3Host.isEmpty()) {
            builder.endpointOverride(URI.create(s3Host));
        }
        
        return builder.build();
    }
}
