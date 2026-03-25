package com.mentorplatform.mentor_platform.service;

import com.mentorplatform.mentor_platform.entity.Role;
import com.mentorplatform.mentor_platform.entity.User;
import com.mentorplatform.mentor_platform.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Single service that satisfies both application-level user management
 * and Spring Security's UserDetailsService contract.
 *
 * Spring Security calls loadUserByUsername() during every authentication.
 * The returned UserDetails is what gets stored in the SecurityContext
 * and passed to JwtUtil.isTokenValid().
 */
@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------
    // UserDetailsService — called by Spring Security + JwtAuthFilter
    // ---------------------------------------------------------------

    /**
     * Loads a user by email (our "username" field).
     * Grants the authority "ROLE_MENTOR" or "ROLE_STUDENT" so that
     * @PreAuthorize("hasRole('MENTOR')") works out of the box.
     *
     * @throws UsernameNotFoundException if no user exists for the email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),                              // already BCrypt-hashed
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()))        // e.g. ROLE_MENTOR
        );
    }

    // ---------------------------------------------------------------
    // Application-level operations (called by AuthController)
    // ---------------------------------------------------------------

    /**
     * Registers a new user.
     *
     * @param email    unique email address
     * @param password plain-text password (hashed before persistence)
     * @param role     "MENTOR" or "STUDENT" (case-insensitive)
     * @return the persisted Role name, e.g. "MENTOR"
     * @throws IllegalArgumentException if the email is already registered
     *                                  or the role string is invalid
     */
    public String createUser(String email, String password, String role) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email already registered: " + email);
        }

        Role resolvedRole = parseRole(role);

        User user = new User();
        user.setEmail(email.toLowerCase().strip());
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(resolvedRole);

        userRepository.save(user);
        return resolvedRole.name();
    }

    /**
     * Returns the role name for the given email.
     * Used by AuthController after a successful login to embed the
     * role in the JWT claims.
     *
     * @throws UsernameNotFoundException if the email does not exist
     */
    @Transactional(readOnly = true)
    public String getRoleByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> u.getRole().name())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));
    }

    /**
     * Fetches the full {@link User} entity by email.
     * Useful when session controllers need the internal UUID, not
     * just the Spring Security principal.
     *
     * @throws UsernameNotFoundException if the email does not exist
     */
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with email: " + email));
    }

    /**
     * Fetches a user by their UUID (used by SessionService when resolving
     * mentor_id / student_id foreign keys).
     *
     * @throws UsernameNotFoundException if the id does not exist
     */
    @Transactional(readOnly = true)
    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with id: " + id));
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.toUpperCase().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid role '" + role + "'. Must be MENTOR or STUDENT.");
        }
    }
}