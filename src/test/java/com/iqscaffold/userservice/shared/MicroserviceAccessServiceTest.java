package com.iqscaffold.userservice.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.iqscaffold.userservice.config.PlatformConfigurationProperties;
import com.iqscaffold.userservice.usermanagement.User;
import com.iqscaffold.userservice.usermanagement.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicroserviceAccessServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ConfigurableFeatureService configurableFeatureService;

  @Mock
  private PlatformConfigurationProperties platformConfig;

  @Mock
  private PlatformConfigurationProperties.Features featuresConfig;

  @InjectMocks
  private MicroserviceAccessService microserviceAccessService;

  @BeforeEach
  void setUp() {
    // Lenient stubbing to avoid UnnecessaryStubbingException
    org.mockito.Mockito.lenient().when(platformConfig.features()).thenReturn(featuresConfig);
  }

  @Test
  @DisplayName("Should get user microservices by user ID")
  void shouldGetUserMicroservicesByUserId() {
    // Arrange
    var userId = 1L;
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of("crm-service"), List.of(), List.of(), true, true
    );

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.getUserFeatures(user)).thenReturn(List.of("crm"));
    when(featuresConfig.getFeature("crm")).thenReturn(featureDefinition);

    // Act
    var result = microserviceAccessService.getUserMicroservices(userId);

    // Assert
    assertThat(result).contains("crm-service");
  }

  @Test
  @DisplayName("Should return empty set when user not found")
  void shouldReturnEmptySetWhenUserNotFound() {
    // Arrange
    var userId = 999L;
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // Act
    var result = microserviceAccessService.getUserMicroservices(userId);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should check if user has microservice access")
  void shouldCheckIfUserHasMicroserviceAccess() {
    // Arrange
    var userId = 1L;
    var microservice = "crm-service";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of(microservice), List.of(), List.of(), true, true
    );

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.getUserFeatures(user)).thenReturn(List.of("crm"));
    when(featuresConfig.getFeature("crm")).thenReturn(featureDefinition);

    // Act
    var result = microserviceAccessService.hasMicroserviceAccess(userId, microservice);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return false when user does not have microservice access")
  void shouldReturnFalseWhenUserDoesNotHaveMicroserviceAccess() {
    // Arrange
    var userId = 1L;
    var microservice = "billing-service";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.getUserFeatures(user)).thenReturn(List.of());

    // Act
    var result = microserviceAccessService.hasMicroserviceAccess(userId, microservice);

    // Assert
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should check if user has route access")
  void shouldCheckIfUserHasRouteAccess() {
    // Arrange
    var userId = 1L;
    var route = "/api/v1/crm/contacts";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.findFeatureByRoute(route)).thenReturn("crm");
    when(configurableFeatureService.hasFeatureAccess(user, "crm")).thenReturn(true);

    // Act
    var result = microserviceAccessService.hasRouteAccess(userId, route);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should allow access to unprotected routes")
  void shouldAllowAccessToUnprotectedRoutes() {
    // Arrange
    var userId = 1L;
    var route = "/api/v1/public/health";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.findFeatureByRoute(route)).thenReturn(null);

    // Act
    var result = microserviceAccessService.hasRouteAccess(userId, route);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should validate access successfully")
  void shouldValidateAccessSuccessfully() {
    // Arrange
    var userId = 1L;
    var microservice = "crm-service";
    var endpoint = "/api/v1/crm/contacts";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");

    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of(microservice), List.of(), List.of(), true, true
    );

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(configurableFeatureService.getUserFeatures(user)).thenReturn(List.of("crm"));
    when(featuresConfig.getFeature("crm")).thenReturn(featureDefinition);
    when(configurableFeatureService.findFeatureByRoute(endpoint)).thenReturn("crm");
    when(configurableFeatureService.hasFeatureAccess(user, "crm")).thenReturn(true);

    // Act
    var result = microserviceAccessService.validateAccess(userId, microservice, endpoint);

    // Assert
    assertThat(result.hasAccess()).isTrue();
    assertThat(result.message()).isEqualTo("Access granted");
  }

  @Test
  @DisplayName("Should deny access when user not found")
  void shouldDenyAccessWhenUserNotFound() {
    // Arrange
    var userId = 999L;
    var microservice = "crm-service";
    var endpoint = "/api/v1/crm/contacts";

    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // Act
    var result = microserviceAccessService.validateAccess(userId, microservice, endpoint);

    // Assert
    assertThat(result.hasAccess()).isFalse();
    assertThat(result.message()).isEqualTo("User not found");
  }
}
