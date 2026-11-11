package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@PreAuthorize("hasRole('OWNER')")
public class TruckController {

    private final TruckService service;

    public TruckController(TruckService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TruckResponse> create(@Valid @RequestBody TruckRequest request,
                                                Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTruck(principal.getName(), request));
    }

    @GetMapping("/my")
    public List<TruckResponse> myTrucks(Principal principal) {
        return service.myTrucks(principal.getName());
    }

    @PutMapping("/{id}")
    public TruckResponse update(@PathVariable Long id,
                                @Valid @RequestBody TruckRequest request,
                                Principal principal) {
        return service.updateMyTruck(id, principal.getName(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        service.deleteMyTruck(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/availability")
    public TruckResponse setAvailability(@PathVariable Long id,
                                         @RequestParam boolean available,
                                         Principal principal) {
        return service.setAvailability(id, principal.getName(), available);
    }
}
