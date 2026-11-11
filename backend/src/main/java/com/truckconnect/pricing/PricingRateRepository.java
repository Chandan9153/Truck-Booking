package com.truckconnect.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PricingRateRepository extends JpaRepository<PricingRate, Long> {
    Optional<PricingRate> findByCategoryIdAndActiveTrue(Long categoryId);
    List<PricingRate> findAllByActiveTrue();
    boolean existsByCategoryId(Long categoryId);
}
