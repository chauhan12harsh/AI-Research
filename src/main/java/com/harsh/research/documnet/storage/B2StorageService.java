package com.harsh.research.documnet.storage;

import com.harsh.research.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class B2StorageService implements StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length cannot be negative");
        }

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(storageProperties.bucket())
                        .key(key)
                        .contentLength(contentLength)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(data, contentLength)
        );
    }

    @Override
    public InputStream get(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(storageProperties.bucket())
                        .key(key)
                        .build()
        );
    }

    @Override
    public String getAsString(String key) {
        try (InputStream input = get(key);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read object " + key, exception);
        }
    }

    @Override
    public PresignedUrl presignedGet(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Presigned URL lifetime must be positive");
        }

        var presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(
                                GetObjectRequest.builder()
                                        .bucket(storageProperties.bucket())
                                        .key(key)
                                        .build()
                        )
                        .build()
        );

        return new PresignedUrl(presigned.url().toString(), Instant.now().plus(ttl));
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(storageProperties.bucket())
                        .key(key)
                        .build()
        );
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .build()
            );
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }
}
