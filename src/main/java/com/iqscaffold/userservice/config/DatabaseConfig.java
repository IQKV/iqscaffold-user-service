package com.iqscaffold.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Database configuration for User Service. Configures JPA repositories and transaction management.
 * Entity scanning is handled automatically by Spring Boot's @SpringBootApplication.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.iqscaffold.userservice.usermanagement",
        "com.iqscaffold.userservice.tenancy",
        "com.iqscaffold.userservice.organization",
        "com.iqscaffold.userservice.emailverification",
        "com.iqscaffold.userservice.security",
        "com.iqscaffold.userservice.shared"
    },
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
@EnableTransactionManagement
public class DatabaseConfig {
  // Entities and repositories are organized by domain modules
  // Entity scanning is handled by @SpringBootApplication
}