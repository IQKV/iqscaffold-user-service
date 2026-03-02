package com.iqscaffold.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Temporary debug filter to log all incoming request headers.
 * This helps diagnose why the Authorization header is not being processed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DebugHeaderFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(DebugHeaderFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    
    String path = request.getRequestURI();
    
    // Only log for the preferences endpoint
    if (path.contains("/preferences")) {
      logger.info("=== DEBUG: Incoming Request to {} ===", path);
      logger.info("Method: {}", request.getMethod());
      
      // Log all headers
      var headerNames = Collections.list(request.getHeaderNames());
      for (String headerName : headerNames) {
        String headerValue = request.getHeader(headerName);
        if (headerName.equalsIgnoreCase("authorization")) {
          // Mask the token for security, but show it exists
          logger.info("Header: {} = {} (length: {})", 
              headerName, 
              headerValue != null ? headerValue.substring(0, Math.min(20, headerValue.length())) + "..." : "null",
              headerValue != null ? headerValue.length() : 0);
        } else {
          logger.info("Header: {} = {}", headerName, headerValue);
        }
      }
      logger.info("=== END DEBUG ===");
    }
    
    filterChain.doFilter(request, response);
  }
}
