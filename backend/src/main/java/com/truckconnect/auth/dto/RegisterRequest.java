package com.truckconnect.auth.dto;

import com.truckconnect.user.Role;
import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[6-9][0-9]{9}$",
             message = "Phone must be a 10-digit Indian mobile number")
    String phone,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
    String password,

    @NotNull(message = "Role is required")
    Role role
) {}
