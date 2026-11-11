package com.truckconnect.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DisputeResolveRequest(
        @NotNull String resolution,   // COMPLETED or CANCELLED — the terminal state
        @NotBlank String adminNote    // required: why this decision was made
) { }
