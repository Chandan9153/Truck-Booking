package com.truckconnect.loadpost.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BookFromLoadPostRequest(
        @NotEmpty(message = "Choose at least one truck to book")
        @Size(max = 25, message = "Cannot book more than 25 trucks at once")
        List<Long> quoteIds
) { }
