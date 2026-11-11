package com.truckconnect.pricing;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    /** The result of one price calculation, with every step visible. */
    public record FareBreakdown(
            BigDecimal chargeableTons,
            boolean minimumChargeApplied,
            BigDecimal baseFare,
            BigDecimal tollCharges,
            BigDecimal loadingCharges,
            BigDecimal platformFee,
            BigDecimal totalFare) { }

    /**
     * distanceKm  : kilometres, already validated (1..5000)
     * loadTons    : the customer's actual load
     * payloadTons : this truck's declared capacity
     * ratePerTonKm: from the rate table, by the truck's category
     * toll / loading: estimates from the search request (0 if unknown)
     * platformFee / minimumLoadFraction: from platform_settings
     */
    public FareBreakdown calculate(BigDecimal distanceKm,
                                   BigDecimal loadTons,
                                   BigDecimal payloadTons,
                                   BigDecimal ratePerTonKm,
                                   BigDecimal tollCharges,
                                   BigDecimal loadingCharges,
                                   BigDecimal platformFee,
                                   BigDecimal minimumLoadFraction) {
        BigDecimal minimumTons = payloadTons.multiply(minimumLoadFraction);
        boolean minimumApplied = loadTons.compareTo(minimumTons) < 0;
        BigDecimal chargeableTons = minimumApplied ? minimumTons : loadTons;

        BigDecimal baseFare = distanceKm
                .multiply(ratePerTonKm)
                .multiply(chargeableTons)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = baseFare
                .add(tollCharges).add(loadingCharges).add(platformFee)
                .setScale(2, RoundingMode.HALF_UP);

        return new FareBreakdown(chargeableTons, minimumApplied, baseFare,
                tollCharges, loadingCharges, platformFee, total);
    }
}
