package com.truckconnect.loadpost.dto;

import com.truckconnect.quote.dto.QuoteResponse;
import java.math.BigDecimal;
import java.util.List;

public record LoadPostMatchResponse(
        Long postId,
        BigDecimal tonsPerTruck,
        int trucksNeeded,
        int trucksBooked,
        int trucksRemaining,
        List<Match> matches,
        BigDecimal cheapestPerTruckFare,          // null when no matches
        BigDecimal estimatedTotalForRemainingTrucks, // cheapest x remaining; estimate only
        String estimateNote
) {
    /** One matching truck: the truck, its quote, and its fare. */
    public record Match(
            Long truckId,
            String registrationNumber,
            String categoryName,
            int tyreCount,
            BigDecimal payloadTons,
            String bodyType,
            String ownerName,
            QuoteResponse quote
    ) { }
}
