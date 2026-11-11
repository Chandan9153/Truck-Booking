package com.truckconnect.truck;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class TruckCategorySeeder implements CommandLineRunner {

    private final TruckCategoryRepository repository;

    public TruckCategorySeeder(TruckCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
            new TruckCategory("12 Tyre Truck", 12, new BigDecimal("20.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("14 Tyre Truck", 14, new BigDecimal("25.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("18 Tyre Truck", 18, new BigDecimal("30.00"),
                    "Target category - verified payload is per vehicle, from its RC"),
            new TruckCategory("22 Tyre Truck", 22, new BigDecimal("36.00"),
                    "Target category - verified payload is per vehicle, from its RC")
        ));
    }
}
