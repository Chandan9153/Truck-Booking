package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/truck-categories")
public class TruckCategoryController {

    private final TruckCategoryService service;

    public TruckCategoryController(TruckCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<TruckCategoryResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TruckCategoryResponse> listAll() {
        return service.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TruckCategoryResponse> create(
            @Valid @RequestBody TruckCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TruckCategoryResponse update(@PathVariable Long id,
            @Valid @RequestBody TruckCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
