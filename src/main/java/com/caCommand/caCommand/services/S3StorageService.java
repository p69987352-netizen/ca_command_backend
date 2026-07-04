package com.caCommand.caCommand.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Service
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;
    private final String endpointUrl;
    private final String region;

    public S3StorageService(@Value("${aws.s3.bucket:}") String bucketName,
                            @Value("${aws.s3.access-key:}") String accessKey,
                            @Value("${aws.s3.secret-key:}") String secretKey,
                            @Value("${aws.s3.region:}") String region,
                            @Value("${aws.s3.endpoint:}") String endpointUrl) {
        this.bucketName = bucketName;
        this.endpointUrl = endpointUrl;
        this.region = region;

        if (bucketName == null || bucketName.isBlank() ||
            accessKey == null || accessKey.isBlank() ||
            secretKey == null || secretKey.isBlank() ||
            region == null || region.isBlank()) {
            log.warn("AWS S3 credentials or configuration are not provided. S3 features will be disabled.");
            this.s3Client = null;
            this.s3Presigner = null;
            return;
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .crossRegionAccessEnabled(true)
                .credentialsProvider(StaticCredentialsProvider.create(credentials));
        
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        this.s3Client = builder.build();

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (endpointUrl != null && !endpointUrl.isBlank()) {
            presignerBuilder.endpointOverride(URI.create(endpointUrl));
        }
        this.s3Presigner = presignerBuilder.build();
    }

    public String uploadMedia(byte[] fileBytes, String fileName) {
        if (s3Client == null) {
            log.error("S3 client is not initialized. Cannot upload file={}", fileName);
            return null;
        }
        try {
            boolean isPdf = fileBytes.length > 4 && 
                            fileBytes[0] == 0x25 && // %
                            fileBytes[1] == 0x50 && // P
                            fileBytes[2] == 0x44 && // D
                            fileBytes[3] == 0x46;   // F

            String extension = isPdf ? ".pdf" : "";
            if (!fileName.contains(".") && !isPdf) {
                extension = ".jpg"; // fallback 
            }

            String keyName = "ca_docs/" + fileName + "_" + System.currentTimeMillis() + extension;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(keyName)
                    .contentType(isPdf ? "application/pdf" : "image/jpeg")
                    // Cost Optimization: INTELLIGENT_TIERING automatically moves unused files to cheaper storage tiers
                    .storageClass(StorageClass.INTELLIGENT_TIERING)
                    // Cost Optimization: Aggressive caching to reduce egress bandwidth costs when viewed multiple times
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));

            // Return the S3 URI format so we can identify it later for presigning
            // Format: s3://bucketName/keyName
            return "s3://" + bucketName + "/" + keyName;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            log.warn("S3 upload failed for fileName={}. AWS Error: {} (Status: {})", fileName, e.awsErrorDetails().errorMessage(), e.statusCode());
            return null;
        } catch (Exception e) {
            log.warn("S3 upload failed for fileName={}. Error: {}", fileName, e.getMessage());
            return null;
        }
    }

    public String getSignedUrl(String urlStr) {
        if (s3Presigner == null) {
            log.warn("S3 presigner is not initialized. Returning raw URL={}", urlStr);
            return urlStr;
        }
        try {
            // Check if it's an S3 URL
            if (!urlStr.startsWith("s3://")) {
                return urlStr; // Not an S3 URL, return as is
            }

            // Parse s3://bucketName/keyName
            String path = urlStr.substring(5); // remove s3://
            int slashIndex = path.indexOf('/');
            if (slashIndex == -1) return urlStr;

            String bucket = path.substring(0, slashIndex);
            String key = path.substring(slashIndex + 1);

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofDays(7)) // 7 days expiration
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            log.warn("Failed to sign S3 URL: {}", urlStr, e);
            return urlStr;
        }
    }
    @Override
    public File downloadMediaLocally(String urlStr) throws Exception {
        if (s3Client == null) {
            throw new IllegalStateException("S3 client is not initialized. S3 storage features are disabled.");
        }
        if (!urlStr.startsWith("s3://")) {
            throw new IllegalArgumentException("Not an S3 URL: " + urlStr);
        }

        String path = urlStr.substring(5);
        int slashIndex = path.indexOf('/');
        if (slashIndex == -1) throw new IllegalArgumentException("Invalid S3 URL format");

        String bucket = path.substring(0, slashIndex);
        String key = path.substring(slashIndex + 1);

        File tempFile = File.createTempFile("ca_doc_", ".tmp");
        tempFile.delete(); // Delete the empty file so AWS SDK can create it without FileAlreadyExistsException
        
        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.getObject(getObjectRequest, tempFile.toPath());
        
        return tempFile;
    }
}
