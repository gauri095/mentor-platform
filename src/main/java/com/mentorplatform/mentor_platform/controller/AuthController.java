package com.mentorplatform.mentor_platform.controller;

import com.mentorplatform.mentor_platform.dto.AuthResponse;
import com.mentorplatform.mentor_platform.dto.LoginRequest;
import com.mentorplatform.mentor_platform.dto.RegisterRequest;
import com.mentorplatform.mentor_platform.security.JwtUtil;
import com.mentorplatform.mentor_platform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST endpoints.
 *
 *  POST /api/auth/register  → create account           → 201 + token pair
 *  POST /api/auth/login     → authenticate             → 200 + token pair
 *  POST /api/auth/refresh   → rotate tokens            → 200 + new token pair
 *
 * All endpoints are public (no JWT required) — configured in SecurityConfig.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final UserDetailsService    userDetailsService;
    private final UserService           userService;
    private final JwtUtil               jwtUtil;

    public AuthController(AuthenticationManager authManager,
                          UserDetailsService    userDetailsService,
                          UserService           userService,
                          JwtUtil               jwtUtil) {
        this.authManager        = authManager;
        this.userDetailsService = userDetailsService;
        this.userService        = userService;
        this.jwtUtil            = jwtUtil;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/register
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new user account and returns a ready-to-use token pair.
     *
     * Possible error responses:
     *   400 — validation failure (blank fields, invalid email / role format)
     *   409 — email already registered  (thrown by UserService, caught by GlobalExceptionHandler)
     *
     * @param req validated request body
     * @return 201 Created + {@link AuthResponse}
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {

        // 1. Persist — UserService hashes the password and validates the role
        String role = userService.createUser(req.email(), req.password(), req.role());

        // 2. Load the saved principal and issue tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.email());
        AuthResponse body = AuthResponse.of(
                jwtUtil.generateAccessToken(userDetails, role),
                jwtUtil.generateRefreshToken(userDetails),
                role
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by email + password and returns a token pair.
     *
     * Possible error responses:
     *   400 — validation failure (blank fields, invalid email format)
     *   401 — wrong credentials  (BadCredentialsException → GlobalExceptionHandler)
     *
     * @param req validated request body
     * @return 200 OK + {@link AuthResponse}
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {

        // 1. Verify credentials — throws BadCredentialsException on failure
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        // 2. Load principal and role, then issue tokens
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.email());
        String role = userService.getRoleByEmail(req.email());

        return ResponseEntity.ok(AuthResponse.of(
                jwtUtil.generateAccessToken(userDetails, role),
                jwtUtil.generateRefreshToken(userDetails),
                role
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/auth/refresh
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exchanges a valid refresh token for a new access + refresh token pair
     * (rolling refresh — the old refresh token is implicitly invalidated on
     * the client side once the new one is stored).
     *
     * Possible error responses:
     *   401 — token missing, malformed, expired, or is not a refresh token
     *
     * @param refreshToken value from the {@code Refresh-Token} request header
     * @return 200 OK + {@link AuthResponse} with fresh tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Refresh-Token") String refreshToken) {

        // 1. Fast structural check before touching the DB
        if (!jwtUtil.isTokenStructureValid(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Load user and rotate tokens
        String      username    = jwtUtil.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        String      role        = userService.getRoleByEmail(username);

        String newAccess = jwtUtil.refreshAccessToken(refreshToken, userDetails, role);
        if (newAccess == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(AuthResponse.of(
                newAccess,
                jwtUtil.generateRefreshToken(userDetails),
                role
        ));
    }
}