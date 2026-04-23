package com.lawforyou.document.service;

import com.lawforyou.document.config.MinioProperties;
import com.nadeex.spring.exception.BusinessException;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

/**
 * Wraps {@link MinioClient} to provide upload, download and delete operations.
 * All MinIO exceptions are translated into {@link BusinessException} so the
 * global exception handler can return a consistent error response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient     minioClient;
    private final MinioProperties minioProperties;

    /**
     * Ensures the configured bucket exists on application startup.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            String bucket = minioProperties.getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinioStorageService: bucket '{}' created.", bucket);
            } else {
                log.debug("MinioStorageService: bucket '{}' already exists.", bucket);
            }
        } catch (Exception e) {
            log.warn("MinioStorageService: could not verify bucket on startup: {}", e.getMessage());
        }
    }

    /**
     * Uploads a file to MinIO and returns the object key.
     *
     * @param tenantId    owning tenant
     * @param documentId  MongoDB document id
     * @param version     1-based version number
     * @param filename    original filename
     * @param data        file content stream
     * @param size        content length in bytes
     * @param contentType MIME type
     * @return the MinIO object key used to retrieve or delete the file
     */
    public String upload(UUID tenantId, String documentId, int version,
                         String filename, InputStream data, long size, String contentType) {
        String objectKey = buildObjectKey(tenantId, documentId, version, filename);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            log.debug("MinioStorageService: uploaded object '{}'", objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("MinioStorageService: upload failed for '{}': {}", objectKey, e.getMessage());
            throw new BusinessException("Failed to upload document to storage: " + e.getMessage());
        }
    }

    /**
     * Opens a streaming download of a stored object.
     *
     * @param objectKey the key returned by {@link #upload}
     * @return raw input stream — caller is responsible for closing
     */
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinioStorageService: download failed for '{}': {}", objectKey, e.getMessage());
            throw new BusinessException("Failed to download document from storage: " + e.getMessage());
        }
    }

    /**
     * Permanently deletes an object from MinIO.
     *
     * @param objectKey the key returned by {@link #upload}
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectKey)
                    .build());
            log.debug("MinioStorageService: deleted object '{}'", objectKey);
        } catch (Exception e) {
            log.error("MinioStorageService: delete failed for '{}': {}", objectKey, e.getMessage());
            throw new BusinessException("Failed to delete document from storage: " + e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a deterministic, human-readable object key.
     * Pattern: {@code {tenantId}/{documentId}/v{version}/{filename}}
     */
    private String buildObjectKey(UUID tenantId, String documentId, int version, String filename) {
        return String.format("%s/%s/v%d/%s", tenantId, documentId, version, filename);
    }
}

