package com.iqscaffold.userservice.tenancy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantLiquibaseRunner Context Configuration Tests")
class TenantLiquibaseRunnerTest {

  @Mock
  private DataSource dataSource;

  @Test
  @DisplayName("Should handle empty contexts")
  void shouldHandleEmptyContexts() {
    // Should not throw exception during construction
    assertDoesNotThrow(() -> new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "" // empty contexts
    ));
  }

  @Test
  @DisplayName("Should handle demo context")
  void shouldHandleDemoContext() {
    // Should not throw exception during construction
    assertDoesNotThrow(() -> new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "demo"
    ));
  }

  @Test
  @DisplayName("Should handle multiple contexts")
  void shouldHandleMultipleContexts() {
    // Should not throw exception during construction
    assertDoesNotThrow(() -> new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "demo,production,test"
    ));
  }
}
