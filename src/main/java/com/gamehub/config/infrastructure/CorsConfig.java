package com.gamehub.config.infrastructure;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Value("${gamehub.websocket.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * SockJS XHR transports always set {@code xhr.withCredentials = true}.
     * Credentialed browser requests require {@code Access-Control-Allow-Credentials: true}
     * or the browser reports a generic CORS error even when Allow-Origin is present.
     */
    @Value("${gamehub.enable-cors-credentials:false}")
    private boolean enableCorsCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (patterns.isEmpty()) {
            patterns = List.of("*");
        }

        // Credentials + wildcard origin is invalid in browsers; fall back to non-credentialed CORS.
        boolean allowCredentials = enableCorsCredentials && !patterns.contains("*");

        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(allowCredentials);

        System.out.println("CorsConfig: allowedOrigins=" + patterns);
        System.out.println("CorsConfig: allowCredentials=" + allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply to all paths including SockJS endpoints
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Ensure the CorsFilter runs early so preflight requests are handled before security filters
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfigurationSource source = corsConfigurationSource();
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}

