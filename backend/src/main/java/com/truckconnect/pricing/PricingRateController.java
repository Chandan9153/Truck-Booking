package com.truckconnect.pricing;

import com.truckconnect.pricing.dto.PricingRateResponse;
import com.truckconnect.pricing.dto.RateUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/rates")
@PreAuthorize("hasRole('ADMIN')")
public class PricingRateController {

    private final PricingRateRepository rateRepository;

    public PricingRateController(PricingRateRepository rateRepository) {
        this.rateRepository = rateRepository;
    }

    @GetMapping
    public ResponseEntity<List<PricingRateResponse>> list() {
        List<PricingRateResponse> out = rateRepository.findAll().stream()
                .map(r -> new PricingRateResponse(r.getId(),
                        r.getCategory().getName(), r.getCategory().getTyreCount(),
                        r.getCategory().getTargetPayloadTons(),
                        r.getRatePerTonKm(), r.isActive()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PricingRateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RateUpdateRequest req) {
        PricingRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rate not found: " + id));
        rate.setRatePerTonKm(req.ratePerTonKm());
        if (req.active() != null) rate.setActive(req.active());
        rate = rateRepository.save(rate);
        return ResponseEntity.ok(new PricingRateResponse(rate.getId(),
                rate.getCategory().getName(), rate.getCategory().getTyreCount(),
                rate.getCategory().getTargetPayloadTons(),
                rate.getRatePerTonKm(), rate.isActive()));
    }
}
