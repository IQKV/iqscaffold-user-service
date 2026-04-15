package com.iqscaffold.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs active Spring profiles on application startup.
 * Helps verify correct profile configuration in different environments.
 */
@Component
public class StartupProfileLogger {

  private static final Logger logger = LoggerFactory.getLogger(StartupProfileLogger.class);

  private final Environment environment;

  public StartupProfileLogger(final Environment environment) {
    this.environment = environment;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void logActiveProfiles() {
    var activeProfiles = environment.getActiveProfiles();
    var defaultProfiles = environment.getDefaultProfiles();

    logger.info("=".repeat(80));
    logger.info("IQ Key Value User Service - Application Started");
    logger.info("=".repeat(80));

    if (activeProfiles.length > 0) {
      logger.info("Active Spring Profiles: {}", String.join(", ", activeProfiles));
    } else {
      logger.info("Active Spring Profiles: NONE (using default profiles)");
    }

    if (defaultProfiles.length > 0) {
      logger.info("Default Spring Profiles: {}", String.join(", ", defaultProfiles));
    }

    logger.info("Application Name: {}", environment.getProperty("spring.application.name"));
    logger.info("Server Port: {}", environment.getProperty("server.port"));
    logger.info("Management Port: {}", environment.getProperty("management.server.port", "N/A"));
    logger.info("=".repeat(80));
  }
}
