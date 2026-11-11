package com.truckconnect.auth;

import com.truckconnect.auth.dto.*;
import com.truckconnect.security.JwtService;
import com.truckconnect.user.Role;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "ADMIN accounts cannot be created by registration");
        }
        if (userRepository.existsByEmail(
                request.email().trim().toLowerCase())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone().trim());
        // BCrypt hash ONLY. The plain password is never saved.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);

        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(),
                            request.password()));
        } catch (AuthenticationException ex) {
            // Same message for wrong email and wrong password,
            // and for a missing account
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(
                        request.email().trim().toLowerCase())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        String email = jwtService.extractUsername(token); // throws if invalid

        if (!"REFRESH".equals(jwtService.extractType(token))) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid refresh token"));
        if (!jwtService.isTokenValid(token, user)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        // Rotation: a brand-new access token AND refresh token
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails details = user;
        return new AuthResponse(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(),
                jwtService.generateAccessToken(details),
                jwtService.generateRefreshToken(details),
                "Bearer");
    }
}
