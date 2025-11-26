package com.toiter.postservice.service;

import com.toiter.postservice.model.ImageUploadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${s3.public-host:}")
    private String publicHost;

    @Value("${s3.host:}")
    private String s3Host;

    @Value("${s3.presign-duration-days:7}")
    private long presignDurationDays;

    public ImageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public ImageUploadResult uploadImage(MultipartFile file) throws IOException {
        validateFile(file);

        Integer width = null;
        Integer height = null;
        try {
            byte[] fileBytes = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (image != null) {
                width = image.getWidth();
                height = image.getHeight();
                logger.debug("Image dimensions: {}x{}", width, height);
            } else {
                logger.warn("Could not read image dimensions for file: {}", file.getOriginalFilename());
            }
        } catch (Exception e) {
            logger.error("Error reading image dimensions", e);
            // Continue without dimensions rather than failing the upload
        }

        String key = "posts/" + UUID.randomUUID().toString();
        String contentType = file.getContentType();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

        logger.info("Uploaded image with key: {}", key);
        return new ImageUploadResult(key, width, height);
    }

    public String getPublicUrl(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }

        long daysToUse = presignDurationDays;
        if (s3Host != null && s3Host.contains("amazonaws")) {
            daysToUse = Math.min(presignDurationDays, 7);
            if (presignDurationDays > 7) logger.warn("Clamping presign duration from {} to {} for AWS endpoint", presignDurationDays, daysToUse);
        }

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .getObjectRequest(getObjectRequest)
                    .signatureDuration(Duration.ofDays(daysToUse))
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();
            logger.debug("Presigned URL for key {} (truncated): {}", key, url.length() > 200 ? url.substring(0, 200) + "..." : url);
            return url;
        } catch (Exception e) {
            logger.error("Failed to generate presigned public URL for key: {}", key, e);

            try {
                String hostToUse = (publicHost != null && !publicHost.isBlank()) ? publicHost : (s3Host != null && !s3Host.isBlank() ? s3Host : null);
                if (hostToUse != null && !hostToUse.isBlank()) {
                    String safeHost = hostToUse.replaceAll("/+$", "");
                    String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
                    return String.format("%s/%s/%s", safeHost, bucketName, encodedKey);
                }

                GetUrlRequest getUrlRequest = GetUrlRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                return s3Client.utilities().getUrl(getUrlRequest).toString();
            } catch (Exception ex) {
                logger.error("Fallback URL generation also failed for key: {}", key, ex);
                return null;
            }
        }
    }

    public void deleteImage(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Deleted image with key: {}", key);
        } catch (Exception e) {
            logger.error("Failed to delete image with key: {}", key, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: JPEG, PNG, GIF, WebP");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                String extension = originalFilename.substring(lastDotIndex).toLowerCase();
                if (!ALLOWED_EXTENSIONS.contains(extension)) {
                    throw new IllegalArgumentException("File extension not allowed. Allowed extensions: .jpg, .jpeg, .png, .gif, .webp");
                }
            }
        }
    }
}
