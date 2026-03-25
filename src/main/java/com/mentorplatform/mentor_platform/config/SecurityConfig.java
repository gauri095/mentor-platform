package com.mentorplatform.mentor_platform.config;

import com.mentorplatform.mentor_platform.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spring Security filter chain for the Mentor–Student Platform.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  PUBLIC (no token required)                                     │
 * │    POST  /api/auth/register                                     │
 * │    POST  /api/auth/login                                        │
 * │    POST  /api/auth/refresh                                      │
 * │    GET   /ws/**          (WebSocket upgrade — token via STOMP)  │
 * │    GET   /actuator/health                                       │
 * │    GET   /swagger-ui/**  /v3/api-docs/**  (dev only)            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  ROLE-LOCKED                                                    │
 * │    POST  /api/sessions/create  → MENTOR only                    │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  AUTHENTICATED (any valid JWT)                                  │
 * │    everything else                                              │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Fine-grained method-level security is available via
 * {@code @PreAuthorize("hasRole('MENTOR')")} on any controller method.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // activates @PreAuthorize / @PostAuthorize
public class SecurityConfig {

    // Injected from application.properties  →  app.cors.allowed-origins
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    private final JwtAuthFilter        jwtAuthFilter;
    private final UserDetailsService   userDetailsService;
    private final ObjectMapper         objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsService userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter      = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper       = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter chain
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // ── Stateless — no HTTP session, no CSRF token needed ──────────
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── CORS — delegates to corsConfigurationSource() bean ─────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── Security response headers ──────────────────────────────────
                .headers(headers -> headers
                        .frameOptions(fo -> fo.deny())                   // no iframes
                        .contentTypeOptions(ct -> {})                    // X-Content-Type-Options: nosniff
                        .referrerPolicy(rp ->
                                rp.policy(ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )

                // ── Authorization rules ────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth

                        // --- Public: auth endpoints ---
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh"
                        ).permitAll()

                        // --- Public: WebSocket upgrade (STOMP carries the JWT) ---
                        .requestMatchers("/ws/**").permitAll()

                        // --- Public: health check (used by Railway / Render) ---
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                        // --- Public: API docs (lock down in production) ---
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // --- Role-locked: only mentors can create sessions ---
                        .requestMatchers(HttpMethod.POST,
                                "/api/sessions/create"
                        ).hasRole("MENTOR")

                        // --- Everything else requires a valid JWT ---------------
                        .anyRequest().authenticated()
                )

                // ── Custom 401 / 403 JSON responses ───────────────────────────
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Map.of(
                                    "status",    401,
                                    "error",     "Unauthorised — valid JWT required.",
                                    "path",      request.getRequestURI(),
                                    "timestamp", Instant.now().toString()
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(), Map.of(
                                    "status",    403,
                                    "error",     "Forbidden — insufficient role.",
                                    "path",      request.getRequestURI(),
                                    "timestamp", Instant.now().toString()
                            ));
                        })
                )

                // ── JWT filter runs before Spring's username/password filter ──
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Allows the Next.js frontend (and any other origins listed in
     * {@code app.cors.allowed-origins}) to call the API.
     *
     * application.properties examples:
     *   app.cors.allowed-origins=http://localhost:3000               ← dev
     *   app.cors.allowed-origins=https://mentor-app.vercel.app       ← prod
     *   app.cors.allowed-origins=http://localhost:3000,https://...   ← both
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Refresh-Token",      // used by POST /api/auth/refresh
                "X-Requested-With"
        ));
        config.setExposedHeaders(List.of(
                "Authorization"       // lets the frontend read a rotated token if needed
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);      // preflight cache: 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authentication infrastructure
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Wires BCrypt + UserDetailsService into Spring Security's
     * DAO authentication flow (used by AuthenticationManager during login).
     */
   @Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider(userDetailsService);   // ← pass it here
    provider.setPasswordEncoder(passwordEncoder());
    return provider;
}

@Bean
public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}

@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
}