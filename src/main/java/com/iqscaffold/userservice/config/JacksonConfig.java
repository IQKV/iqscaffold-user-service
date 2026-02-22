package com.iqscaffold.userservice.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for JSON serialization/deserialization.
 * 
 * <p>This configuration ensures:
 * <ul>
 *   <li>No Java type information in JSON output (clean RFC 9457 ProblemDetail)</li>
 *   <li>Proper date/time handling with JavaTimeModule</li>
 *   <li>Consistent JSON formatting across the application</li>
 *   <li>Null value handling configuration</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

  /**
   * Configure the primary ObjectMapper bean for the application.
   * 
   * <p>Key configurations:
   * <ul>
   *   <li><strong>No Default Typing</strong> - Prevents Java type information in JSON</li>
   *   <li><strong>JavaTimeModule</strong> - Proper Java 8 date/time serialization</li>
   *   <li><strong>Non-null Inclusion</strong> - Excludes null values from JSON output</li>
   *   <li><strong>Indented Output</strong> - Pretty-printed JSON for readability</li>
   * </ul>
   * 
   * @return Configured ObjectMapper instance
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    
    // Disable default typing to prevent Java type information in JSON
    // This ensures ProblemDetail responses don't include ["org.springframework.http.ProblemDetail", ...]
    mapper.deactivateDefaultTyping();
    
    // Register JavaTimeModule for proper date/time handling
    mapper.registerModule(new JavaTimeModule());
    
    // Configure serialization features
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    
    // Exclude null values from JSON output
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    return mapper;
  }
}
