package com.gamehub.config.infrastructure;

import java.util.Arrays;
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

    @Value("${gamehub.websocket.allowed-origins:https://boardgame-verse.vercel.app,https://preview--boardgame-verse.lovable.app,https://*.vercel.app,https://*.lovable.app,http://localhost:5173,http://localhost:4173,http://192.168.31.103:4173}")
    private String allowedOrigins;

    @Value("${gamehub.enable-cors-credentials:false}")
    private boolean enableCredentials;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        // Use allowed origin patterns to support dynamic hosts (ngrok)
        for (String o : origins) {
            config.addAllowedOriginPattern(o);
        }
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(enableCredentials);

        System.out.println("CorsConfig: allowedOrigins=" + Arrays.toString(origins));
        System.out.println("CorsConfig: allowCredentials=" + enableCredentials);

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

