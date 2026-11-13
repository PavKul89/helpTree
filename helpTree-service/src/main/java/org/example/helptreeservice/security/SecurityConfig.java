package org.example.helptreeservice.security;

import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.filter.RateLimiterFilter;
import org.example.helptreeservice.security.JwtAuthFilter;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimiterFilter rateLimiterFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimiterFilter rateLimiterFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimiterFilter = rateLimiterFilter;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * The limiter must run inside the security chain, immediately after the JWT
     * filter: only then does it know the authenticated account. Disabling the
     * servlet auto-registration prevents it from running a second time outside
     * the chain.
     */
    @Bean
    public FilterRegistrationBean<RateLimiterFilter> rateLimiterFilterRegistration() {
        FilterRegistrationBean<RateLimiterFilter> registration =
                new FilterRegistrationBean<>(rateLimiterFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Order(1)
    @Conditional(PrometheusScrapeCredentialsConfigured.class)
    public SecurityFilterChain prometheusSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${app.metrics.prometheus.username:prometheus}") String username,
            @Value("${app.metrics.prometheus.password:}") String password) throws Exception {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "PROMETHEUS_SCRAPE_USERNAME and PROMETHEUS_SCRAPE_PASSWORD must be configured");
        }
        UserDetailsService prometheusUserDetailsService = new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("PROMETHEUS")
                        .build());
        http
            .securityMatcher("/actuator/prometheus")
            .userDetailsService(prometheusUserDetailsService)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("PROMETHEUS"))
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    public static class PrometheusScrapeCredentialsConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String password = context.getEnvironment()
                    .getProperty("app.metrics.prometheus.password");
            return password != null && !password.isBlank();
        }
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimiterFilter, JwtAuthFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Everything else is administrative: metrics, loggers and any
                // endpoint exposed later must never be readable anonymously.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/swagger-ui/**", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/password/reset/request",
                        "/api/auth/password/reset/confirm",
                        "/api/auth/email/verification/confirm",
                        "/api/auth/email/change/confirm").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/stats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/helps/graph").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/helps/stats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ratings/**").permitAll()
                .requestMatchers("/api/achievements").permitAll()
                .requestMatchers("/api/activities").permitAll()
                // Defense in depth: the admin controllers re-check the role, but a
                // misconfigured controller can never be reached by a non-admin.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                // Keep the error envelope identical to @RestControllerAdvice even
                // when Spring Security rejects the request before MVC runs.
                .authenticationEntryPoint((request, response, authException) ->
                        ApiErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                ErrorType.UNAUTHORIZED, "Требуется авторизация"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        ApiErrorWriter.write(request, response, HttpStatus.FORBIDDEN,
                                ErrorType.FORBIDDEN, "Доступ запрещен"))
            );
        return http.build();
    }
}
