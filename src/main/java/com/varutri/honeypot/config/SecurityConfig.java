package com.varutri.honeypot.config;

import com.varutri.honeypot.security.ApiKeyFilter;
import com.varutri.honeypot.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration
 * Filter order: RateLimitFilter (1) -> ApiKeyFilter (2) -> Controller
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // Add rate limit filter first (blocks abusive requests early)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                // Then API key filter (validates authentication)
                .addFilterAfter(apiKeyFilter, RateLimitFilter.class);

        return http.build();
    }
}
