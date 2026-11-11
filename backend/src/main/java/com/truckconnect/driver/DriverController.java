package com.truckconnect.driver;

import com.truckconnect.driver.dto.DriverRequest;
import com.truckconnect.driver.dto.DriverResponse;
import com.truckconnect.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@PreAuthorize("hasAnyRole('OWNER', 'TRANSPORTER')")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public ResponseEntity<DriverResponse> add(
            @AuthenticationPrincipal User owner,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverService.addDriver(owner, request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<DriverResponse>> mine(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(driverService.myDrivers(owner));
    }

    @GetMapping("/mine/active")
    public ResponseEntity<List<DriverResponse>> myActive(
            @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(driverService.myActiveDrivers(owner));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> get(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.getDriver(owner, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DriverResponse> update(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(driverService.updateDriver(owner, id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<DriverResponse> deactivate(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.deactivateDriver(owner, id));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<DriverResponse> activate(
            @AuthenticationPrincipal User owner,
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.activateDriver(owner, id));
    }
}
