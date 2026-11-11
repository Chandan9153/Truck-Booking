package com.truckconnect.pricing;

import com.truckconnect.truck.TruckCategory;
import com.truckconnect.truck.TruckCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

@Component
@Order(2) // Module 2's category seeder is @Order(1): categories must exist first
public class PricingRateSeeder implements CommandLineRunner {

    private final TruckCategoryRepository categoryRepository;
    private final PricingRateRepository rateRepository;
    private final PlatformSettingsRepository settingsRepository;

    public PricingRateSeeder(TruckCategoryRepository categoryRepository,
                             PricingRateRepository rateRepository,
                             PlatformSettingsRepository settingsRepository) {
        this.categoryRepository = categoryRepository;
        this.rateRepository = rateRepository;
        this.settingsRepository = settingsRepository;
    }

    // Category name -> rupees per ton per km (Part 3: checked online, Nov 2026)
    private static final Map<String, String> SEED_RATES = Map.of(
            "12 Tyre Truck", "2.70",
            "14 Tyre Truck", "2.60",
            "18 Tyre Truck", "2.50",
            "22 Tyre Truck", "2.40"
    );

    @Override
    public void run(String... args) {
        for (Map.Entry<String, String> entry : SEED_RATES.entrySet()) {
            TruckCategory category = categoryRepository
                    .findByName(entry.getKey()).orElse(null);
            if (category == null) continue; // category seeder has not run yet
            if (rateRepository.existsByCategoryId(category.getId())) continue;
            rateRepository.save(new PricingRate(
                    category, new BigDecimal(entry.getValue())));
        }
        if (!settingsRepository.existsById(1L)) {
            settingsRepository.save(new PlatformSettings()); // fee ₹0, minimum 50%
        }
    }
}
