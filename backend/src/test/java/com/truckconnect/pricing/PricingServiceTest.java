package com.truckconnect.pricing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    // Module 3, Example 1: 570 km, 20 tons, 12-tyre @ 2.70, toll 1500, loading 1000, fee 0.
    @Test
    void fullLoad_BengaluruToHyderabad_is33280() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("570"), new BigDecimal("20"), new BigDecimal("20"),
                new BigDecimal("2.70"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("20.00"), fare.chargeableTons());
        assertFalse(fare.minimumChargeApplied());
        assertEquals(new BigDecimal("30780.00"), fare.baseFare());
        assertEquals(new BigDecimal("33280.00"), fare.totalFare());
    }

    // Module 3, Example 2: same trip, 8-ton load -> minimum charge (half truck = 10 tons).
    @Test
    void halfEmptyLoad_appliesMinimumCharge_is17890() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("570"), new BigDecimal("8"), new BigDecimal("20"),
                new BigDecimal("2.70"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertTrue(fare.minimumChargeApplied());
        assertEquals(new BigDecimal("10.00"), fare.chargeableTons());
        assertEquals(new BigDecimal("15390.00"), fare.baseFare());
        assertEquals(new BigDecimal("17890.00"), fare.totalFare());
    }

    // Module 3, Example 3: 14-tyre, 25 tons, 500 km @ 2.60 -> base 32500, total 35000.
    @Test
    void biggerTruck_fullLoad_is35000() {
        PricingService.FareBreakdown fare = pricingService.calculate(
                new BigDecimal("500"), new BigDecimal("25"), new BigDecimal("25"),
                new BigDecimal("2.60"), new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("32500.00"), fare.baseFare());
        assertEquals(new BigDecimal("35000.00"), fare.totalFare());
    }
}
