package com.iqscaffold.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify embedded Redis server is working correctly.
 * Currently disabled as it requires external infrastructure setup.
 */
@SpringBootTest
@ActiveProfiles("redis-test")
@Import(TestRedisConfiguration.class)
@Disabled("Redis integration test requires embedded Redis server configuration")
class EmbeddedRedisIntegrationTest {

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  @Test
  void shouldConnectToEmbeddedRedis() {
    // Given
    String key = "test:key";
    String value = "test-value";

    // When
    redisTemplate.opsForValue().set(key, value);
    Object retrieved = redisTemplate.opsForValue().get(key);

    // Then
    assertThat(retrieved).isEqualTo(value);
  }

  @Test
  void shouldHandleRedisOperations() {
    // Given
    String hashKey = "test:hash";

    // When
    redisTemplate.opsForHash().put(hashKey, "field1", "value1");
    redisTemplate.opsForHash().put(hashKey, "field2", "value2");

    // Then
    assertThat(redisTemplate.opsForHash().get(hashKey, "field1")).isEqualTo("value1");
    assertThat(redisTemplate.opsForHash().get(hashKey, "field2")).isEqualTo("value2");
    assertThat(redisTemplate.opsForHash().size(hashKey)).isEqualTo(2);
  }
}

