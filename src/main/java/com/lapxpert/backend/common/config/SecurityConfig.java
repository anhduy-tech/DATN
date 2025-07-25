package com.lapxpert.backend.common.config;

import com.lapxpert.backend.auth.domain.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Bật CORS
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/pos/**").permitAll()
                        .requestMatchers("/api/chat/**").permitAll()
                        .requestMatchers("/api/payment/**").permitAll()
                        .requestMatchers("/api/v1/shipping/config").permitAll() // Allow shipping config for order creation
                        .requestMatchers("/ws/**").permitAll() // Allow WebSocket endpoints
                        .requestMatchers("/error").permitAll() // Cho phép tất cả truy cập /error
                        .requestMatchers("/api/v1/user/**").hasAnyRole("ADMIN", "STAFF")
                                .requestMatchers("/api/v1/thong-ke/**").hasAnyRole("ADMIN", "STAFF")
                                .requestMatchers("/api/v1/shipping/**").hasAnyRole("ADMIN", "STAFF")
                                .requestMatchers("/api/v1/phieu-giam-gia/**").hasAnyRole("ADMIN", "STAFF")
                                .requestMatchers("/api/v1/discounts/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/v1/hoa-don/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/v1/products/**").hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/v1/serial-numbers/**").hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        .requestMatchers("/api/v2/**").permitAll()
                        .requestMatchers("/api/v2/wishlist").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Cho phép preflight request
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins for development
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:3000"
        ));
        // Also allow origin patterns for flexibility
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // Support WebSocket upgrade headers
        configuration.setExposedHeaders(List.of("Upgrade", "Connection", "Sec-WebSocket-Accept"));
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Áp dụng cho tất cả endpoints
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
