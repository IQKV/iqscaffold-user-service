package com.iqscaffold.userservice.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEntityGraphServiceTest {

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private UserEntityGraphService userEntityGraphService;

  @Test
  @DisplayName("Should find user for authentication by username")
  void shouldFindUserForAuthenticationByUsername() {
    // Arrange
    var username = "testuser";
    var user = new User();
    user.setUsername(username);
    when(userRepository.findByUsernameWithAuthorities(username)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.findUserForAuthentication(username);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getUsername()).isEqualTo(username);
    verify(userRepository).findByUsernameWithAuthorities(username);
  }

  @Test
  @DisplayName("Should return empty when user not found by username")
  void shouldReturnEmptyWhenUserNotFoundByUsername() {
    // Arrange
    var username = "nonexistent";
    when(userRepository.findByUsernameWithAuthorities(username)).thenReturn(Optional.empty());

    // Act
    var result = userEntityGraphService.findUserForAuthentication(username);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should find user for authentication by email")
  void shouldFindUserForAuthenticationByEmail() {
    // Arrange
    var email = "test@example.com";
    var user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailWithAuthorities(email)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.findUserForAuthenticationByEmail(email);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getEmail()).isEqualTo(email);
  }

  @Test
  @DisplayName("Should find user for profile with preferences")
  void shouldFindUserForProfile() {
    // Arrange
    var userId = 1L;
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");
    when(userRepository.findByIdWithPreferences(userId)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.findUserForProfile(userId);

    // Assert
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("Should find user with complete profile")
  void shouldFindUserWithCompleteProfile() {
    // Arrange
    var identifier = "testuser";
    var user = new User();
    user.setUsername(identifier);
    when(userRepository.findByUsernameOrEmailWithComplete(identifier, identifier))
        .thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.findUserWithCompleteProfile(identifier);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().getUsername()).isEqualTo(identifier);
  }

  @Test
  @DisplayName("Should find users by role with authorities")
  void shouldFindUsersByRoleWithAuthorities() {
    // Arrange
    var roleName = "ADMIN";
    var user1 = new User();
    user1.setUsername("admin1");
    var user2 = new User();
    user2.setUsername("admin2");
    when(userRepository.findByAuthorityNameWithAuthorities(roleName))
        .thenReturn(List.of(user1, user2));

    // Act
    var result = userEntityGraphService.findUsersByRoleWithAuthorities(roleName);

    // Assert
    assertThat(result).hasSize(2);
    assertThat(result).extracting(User::getUsername).containsExactly("admin1", "admin2");
  }

  @Test
  @DisplayName("Should check if user has authority")
  void shouldCheckIfUserHasAuthority() {
    // Arrange
    var username = "testuser";
    var authorityName = "ADMIN";
    var user = new User();
    user.setUsername(username);
    user.addAuthority(new com.iqscaffold.userservice.shared.Authority(authorityName, "Admin"));
    when(userRepository.findByUsernameWithAuthorities(username)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.userHasAuthority(username, authorityName);

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Should return false when user does not have authority")
  void shouldReturnFalseWhenUserDoesNotHaveAuthority() {
    // Arrange
    var username = "testuser";
    var authorityName = "ADMIN";
    var user = new User();
    user.setUsername(username);
    when(userRepository.findByUsernameWithAuthorities(username)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.userHasAuthority(username, authorityName);

    // Assert
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should return false when user not found for authority check")
  void shouldReturnFalseWhenUserNotFoundForAuthorityCheck() {
    // Arrange
    var username = "nonexistent";
    var authorityName = "ADMIN";
    when(userRepository.findByUsernameWithAuthorities(username)).thenReturn(Optional.empty());

    // Act
    var result = userEntityGraphService.userHasAuthority(username, authorityName);

    // Assert
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Should get user display info")
  void shouldGetUserDisplayInfo() {
    // Arrange
    var userId = 1L;
    var user = new User("testuser", "test@example.com", "hash", "John", "Doe", "tenant-123");
    user.setPreferredLocale("en");
    var preference = new UserPreference();
    preference.setTimezone("America/New_York");
    user.setPreference(preference);
    when(userRepository.findByIdWithPreferences(userId)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.getUserDisplayInfo(userId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.fullName()).isEqualTo("John Doe");
    assertThat(result.locale()).isEqualTo("en");
    assertThat(result.timezone()).isEqualTo("America/New_York");
  }

  @Test
  @DisplayName("Should return null when user not found for display info")
  void shouldReturnNullWhenUserNotFoundForDisplayInfo() {
    // Arrange
    var userId = 999L;
    when(userRepository.findByIdWithPreferences(userId)).thenReturn(Optional.empty());

    // Act
    var result = userEntityGraphService.getUserDisplayInfo(userId);

    // Assert
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("Should use default timezone when preference is null")
  void shouldUseDefaultTimezoneWhenPreferenceIsNull() {
    // Arrange
    var userId = 1L;
    var user = new User("testuser", "test@example.com", "hash", "Jane", "Smith", "tenant-123");
    user.setPreferredLocale("en");
    user.setPreference(null);
    when(userRepository.findByIdWithPreferences(userId)).thenReturn(Optional.of(user));

    // Act
    var result = userEntityGraphService.getUserDisplayInfo(userId);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.timezone()).isEqualTo("UTC");
  }
}
