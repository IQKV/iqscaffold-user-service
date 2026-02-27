package com.iqscaffold.userservice.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.iqscaffold.userservice.infrastructure.repository.dto.TenantDto.CreateTenantRequest;
import com.iqscaffold.userservice.infrastructure.repository.dto.TenantDto.TenantResponse;
import com.iqscaffold.userservice.tenancy.TenantManagementService;
import com.iqscaffold.userservice.tenancy.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultTenantBootstrap Tests")
class DefaultTenantBootstrapTest {

  @Mock
  private TenantRepository tenantRepository;

  @Mock
  private TenantManagementService tenantManagementService;

  @Mock
  private ApplicationArguments args;

  private DefaultTenantBootstrap bootstrap;

  @BeforeEach
  void setUp() {
    bootstrap = new DefaultTenantBootstrap(tenantRepository, tenantManagementService);
    
    // Set default values via reflection
    ReflectionTestUtils.setField(bootstrap, "defaultTenantId", "test-tenant");
    ReflectionTestUtils.setField(bootstrap, "defaultTenantName", "Test Tenant");
    ReflectionTestUtils.setField(bootstrap, "defaultTenantDescription", "Test Description");
    ReflectionTestUtils.setField(bootstrap, "maxUsers", 100);
    ReflectionTestUtils.setField(bootstrap, "storageQuotaGb", 50);
    ReflectionTestUtils.setField(bootstrap, "apiRateLimitPerMinute", 1000);
  }

  @Test
  @DisplayName("Should create default tenant when no tenants exist")
  void shouldCreateDefaultTenantWhenNoneExist() {
    // Given
    when(tenantRepository.count()).thenReturn(0L);
    
    TenantResponse mockResponse = new TenantResponse(
        "test-tenant",
        "Test Tenant",
        "Test Description",
        null,
        100,
        50,
        1000,
        null,
        null,
        null,
        null,
        null
    );
    
    when(tenantManagementService.createTenant(any(CreateTenantRequest.class), eq("system")))
        .thenReturn(mockResponse);

    // When
    bootstrap.run(args);

    // Then
    verify(tenantRepository).count();
    verify(tenantManagementService).createTenant(any(CreateTenantRequest.class), eq("system"));
  }

  @Test
  @DisplayName("Should skip tenant creation when tenants already exist")
  void shouldSkipCreationWhenTenantsExist() {
    // Given
    when(tenantRepository.count()).thenReturn(1L);

    // When
    bootstrap.run(args);

    // Then
    verify(tenantRepository).count();
    verify(tenantManagementService, never()).createTenant(any(), any());
  }

  @Test
  @DisplayName("Should throw exception when tenant creation fails")
  void shouldThrowExceptionWhenCreationFails() {
    // Given
    when(tenantRepository.count()).thenReturn(0L);
    when(tenantManagementService.createTenant(any(CreateTenantRequest.class), eq("system")))
        .thenThrow(new RuntimeException("Database error"));

    // When & Then
    assertThrows(IllegalStateException.class, () -> bootstrap.run(args));
  }

  @Test
  @DisplayName("Should use configured tenant properties")
  void shouldUseConfiguredProperties() {
    // Given
    ReflectionTestUtils.setField(bootstrap, "defaultTenantId", "custom-id");
    ReflectionTestUtils.setField(bootstrap, "defaultTenantName", "Custom Name");
    ReflectionTestUtils.setField(bootstrap, "maxUsers", 200);
    
    when(tenantRepository.count()).thenReturn(0L);
    
    TenantResponse mockResponse = new TenantResponse(
        "custom-id",
        "Custom Name",
        "Test Description",
        null,
        200,
        50,
        1000,
        null,
        null,
        null,
        null,
        null
    );
    
    when(tenantManagementService.createTenant(any(CreateTenantRequest.class), eq("system")))
        .thenReturn(mockResponse);

    // When
    bootstrap.run(args);

    // Then
    verify(tenantManagementService).createTenant(
        argThat(request -> 
            request.tenantId().equals("custom-id") &&
            request.name().equals("Custom Name") &&
            request.maxUsers().equals(200)
        ),
        eq("system")
    );
  }
}
