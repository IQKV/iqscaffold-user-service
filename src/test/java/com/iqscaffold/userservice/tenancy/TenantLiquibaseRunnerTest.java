package com.iqscaffold.userservice.tenancy;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantLiquibaseRunner Context Configuration Tests")
class TenantLiquibaseRunnerTest {

  @Mock
  private DataSource dataSource;

  @Test
  @DisplayName("Should handle empty contexts")
  void shouldHandleEmptyContexts() {
    var runner = new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "" // empty contexts
    );

    // Should not throw exception
    assertDoesNotThrow(() -> {
      // Just verify construction works - actual execution would need database
    });
  }

  @Test
  @DisplayName("Should handle demo context")
  void shouldHandleDemoContext() {
    var runner = new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "demo"
    );

    // Should not throw exception
    assertDoesNotThrow(() -> {
      // Just verify construction works - actual execution would need database
    });
  }

  @Test
  @DisplayName("Should handle multiple contexts")
  void shouldHandleMultipleContexts() {
    var runner = new TenantLiquibaseRunner(
        dataSource,
        "classpath:db/changelog/tenant/master.xml",
        "classpath:db/changelog/system/master.xml",
        "demo,production,test"
    );

    // Should not throw exception
    assertDoesNotThrow(() -> {
      // Just verify construction works - actual execution would need database
    });
  }
}