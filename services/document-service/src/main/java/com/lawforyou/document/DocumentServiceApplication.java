package com.lawforyou.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Document Service.
 *
 * <p>Responsible for document upload/download (MinIO), metadata storage (MongoDB),
 * and publishing {@code DocumentUploadedEvent} to Kafka via the transactional outbox pattern.</p>
 */
@SpringBootApplication
@EnableScheduling
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}

