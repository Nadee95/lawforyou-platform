package com.lawforyou.document.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.minio.*} configuration properties.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
}

