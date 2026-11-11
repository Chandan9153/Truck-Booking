package com.truckconnect.loadpost.dto;

import com.truckconnect.loadpost.LoadPostStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LoadPostResponse(
        Long postId,
        Long postedById,
        String postedByName,
        String goodsDescription,
        BigDecimal totalTons,
        int trucksNeeded,
        BigDecimal tonsPerTruck,
        String categoryName,
        int tyreCount,
        String bodyType,
        String pickup,
        String destination,
        BigDecimal distanceKm,
        LocalDate pickupDate,
        BigDecimal tollCharges,
        BigDecimal loadingCharges,
        LoadPostStatus status,
        int trucksBooked,
        int trucksRemaining,
        LocalDateTime createdAt
) { }
