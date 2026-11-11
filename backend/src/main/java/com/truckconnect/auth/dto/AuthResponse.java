package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;

public record AuthResponse(
    Long userId,
    String fullName,
    String email,
    Role role,
    String accessToken,
    String refreshToken,
    String tokenType   // always "Bearer"
) {}
