package com.iqscaffold.userservice.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for S3/MinIO client setup.
 *
 * <p>Configures the MinIO client for object storage operations with proper
 * connection settings, SSL configuration, and credential management.
 *
 * <h3>Configuration Features</h3>
 * <ul>
 *   <li><strong>MinIO Client</strong> - Configured with endpoint and credentials</li>
 *   <li><strong>SSL Support</strong> - Configurable SSL/TLS for secure connections</li>
 *   <li><strong>Environment-Based</strong> - Uses configuration properties</li>
 *   <li><strong>Connection Validation</strong> - Logs configuration on startup</li>
 * </ul>
 *
 * <h3>Security Notes</h3>
 * <ul>
 *   <li>Credentials are masked in logs for security</li>
 *   <li>SSL should be enabled for production environments</li>
 *   <li>Client is configured as singleton bean</li>
 * </ul>
 *
 * @author IQ Key Value
 */
@Configuration
@EnableConfigurationProperties(S3ConfigurationProperties.class)
@ConditionalOnProperty(name = "iqscaffold.storage.minio.enabled", havingValue = "true", matchIfMissing = true)
public class S3Config {

  private static final Logger log = LoggerFactory.getLogger(S3Config.class);

  private final S3ConfigurationProperties s3Properties;

  public S3Config(final S3ConfigurationProperties s3Properties) {
    this.s3Properties = s3Properties;
  }

  /**
   * Creates and configures MinIO client bean.
   *
   * @return configured MinIO client instance
   */
  @Bean
  public MinioClient minioClient() {
    log.info("Configuring MinIO client - Endpoint: {}, Bucket: {}, SSL: {}",
        s3Properties.endpoint(),
        s3Properties.bucketName(),
        s3Properties.ssl());

    var clientBuilder = MinioClient.builder()
        .endpoint(s3Properties.endpoint())
        .credentials(s3Properties.accessKey(), s3Properties.secretKey());

    // Configure SSL if specified
    if (!s3Properties.ssl()) {
      log.warn("SSL is disabled for MinIO client - this should only be used in development");
    }

    // Set region if provided
    if (s3Properties.region() != null && !s3Properties.region().isBlank()) {
      clientBuilder.region(s3Properties.region());
    }

    var client = clientBuilder.build();

    log.info("MinIO client configured successfully");
    return client;
  }
}
