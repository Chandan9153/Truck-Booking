package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;
import com.truckconnect.user.User;

public record UserResponse(Long id, String fullName, String email,
                           String phone, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getRole());
    }
}
