package com.truckconnect.admin.dto;

public record AdminUserResponse(
        Long id, String name, String email, String role, boolean active) { }
