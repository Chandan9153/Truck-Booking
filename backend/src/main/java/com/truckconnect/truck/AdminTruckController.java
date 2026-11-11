package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

// Slim registration (Module 3 Part 0): there is no admin paper check any more,
// so this controller only lists trucks. Categories and rates are managed by
// TruckCategoryController and PricingRateController.
@RestController
@RequestMapping("/api/admin/trucks")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTruckController {

    private final TruckService service;

    public AdminTruckController(TruckService service) {
        this.service = service;
    }

    @GetMapping
    public List<TruckResponse> all() {
        return service.allTrucks();
    }
}
