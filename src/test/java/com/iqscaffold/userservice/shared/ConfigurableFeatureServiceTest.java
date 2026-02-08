package com.iqscaffold.userservice.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
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
class ConfigurableFeatureServiceTest {

  @Mock
  private PlatformConfigurationProperties platformConfig;

  @Mock
  private AuthorityRepository authorityRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private PlatformConfigurationProperties.Features featuresConfig;

  @Mock
  private PlatformConfigurationProperties.Authorities authoritiesConfig;

  @InjectMocks
  private ConfigurableFeatureService configurableFeatureService;

  @BeforeEach
  void setUp() {
    // Lenient stubbing to avoid UnnecessaryStubbingException
    org.mockito.Mockito.lenient().when(platformConfig.features()).thenReturn(featuresConfig);
    org.mockito.Mockito.lenient().when(platformConfig.authorities()).thenReturn(authoritiesConfig);
  }

  @Test
  @DisplayName("Should check if user has feature access")
  void shouldCheckIfUserHasFeatureAccess() {
    // Arrange
    var userId = 1L;
    var featureCode = "crm";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");
    var authority = new Authority("CRM_ACCESS", "CRM Access");
    user.addAuthority(authority);

    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of(), List.of(), List.of(), true, true
    );

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(featuresConfig.getFeature(featureCode)).thenReturn(featureDefinition);
    when(authoritiesConfig.isAdminAuthority(any())).thenReturn(false);

    // Act
    var result = configurableFeatureService.hasFeatureAccess(userId, featureCode);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return false when user not found")
  void shouldReturnFalseWhenUserNotFound() {
    // Arrange
    var userId = 999L;
    var featureCode = "crm";
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // Act
    var result = configurableFeatureService.hasFeatureAccess(userId, featureCode);

    // Assert
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should return false when feature not found")
  void shouldReturnFalseWhenFeatureNotFound() {
    // Arrange
    var userId = 1L;
    var featureCode = "nonexistent";
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(featuresConfig.getFeature(featureCode)).thenReturn(null);

    // Act
    var result = configurableFeatureService.hasFeatureAccess(userId, featureCode);

    // Assert
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should get user features")
  void shouldGetUserFeatures() {
    // Arrange
    var userId = 1L;
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");
    var authority = new Authority("CRM_ACCESS", "CRM Access");
    user.addAuthority(authority);

    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of(), List.of(), List.of(), true, true
    );

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(featuresConfig.getComposableFeatures()).thenReturn(Map.of("crm", featureDefinition));
    when(featuresConfig.getFeature("crm")).thenReturn(featureDefinition);
    when(authoritiesConfig.isAdminAuthority(any())).thenReturn(false);

    // Act
    var result = configurableFeatureService.getUserFeatures(userId);

    // Assert
    assertThat(result).contains("crm");
  }

  @Test
  @DisplayName("Should get feature definition")
  void shouldGetFeatureDefinition() {
    // Arrange
    var featureCode = "crm";
    var featureDefinition = new PlatformConfigurationProperties.FeatureDefinition(
        "CRM", "Customer Relationship Management",
        List.of("CRM_ACCESS"), List.of("CRM_ACCESS"), List.of(), List.of(), List.of(), true, true
    );

    when(featuresConfig.getFeature(featureCode)).thenReturn(featureDefinition);

    // Act
    var result = configurableFeatureService.getFeatureDefinition(featureCode);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.displayName()).isEqualTo("CRM");
  }
}
