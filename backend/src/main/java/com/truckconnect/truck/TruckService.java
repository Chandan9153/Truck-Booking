package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import com.truckconnect.user.User;
import com.truckconnect.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TruckService {

    private final TruckRepository truckRepository;
    private final TruckCategoryService categoryService;
    private final UserRepository userRepository;

    public TruckService(TruckRepository truckRepository,
                        TruckCategoryService categoryService,
                        UserRepository userRepository) {
        this.truckRepository = truckRepository;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    // The logged-in user, found from the email inside the JWT (Module 1).
    private User currentUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() ->
                new IllegalArgumentException("Logged-in user not found: " + email));
    }

    @Transactional
    public TruckResponse createTruck(String ownerEmail, TruckRequest req) {
        User owner = currentUser(ownerEmail);
        String regNo = req.registrationNumber().replaceAll("\\s+", "").toUpperCase();
        if (truckRepository.existsByRegistrationNumber(regNo)) {
            throw new IllegalArgumentException("Registration number already exists: " + regNo);
        }
        if (req.permittedPayloadTons().compareTo(req.grossVehicleWeightTons()) >= 0) {
            throw new IllegalArgumentException(
                "Permitted payload must be less than gross vehicle weight");
        }
        Truck truck = new Truck();
        applyRequest(truck, req, regNo);
        truck.setOwner(owner);
        // Slim registration (Module 3 Part 0): no paper check. A new truck starts
        // UNAVAILABLE; the owner makes it AVAILABLE when it is ready to book.
        truck.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        return TruckResponse.from(truckRepository.save(truck));
    }

    private void applyRequest(Truck truck, TruckRequest req, String regNo) {
        truck.setRegistrationNumber(regNo);
        truck.setCategory(categoryService.getActiveOrThrow(req.categoryId()));
        truck.setTyreCount(req.tyreCount());
        truck.setPermittedPayloadTons(req.permittedPayloadTons());
        truck.setGrossVehicleWeightTons(req.grossVehicleWeightTons());
        truck.setBodyType(req.bodyType());
        truck.setLengthFeet(req.lengthFeet());
        truck.setWidthFeet(req.widthFeet());
        truck.setHeightFeet(req.heightFeet());
    }

    public List<TruckResponse> myTrucks(String ownerEmail) {
        User owner = currentUser(ownerEmail);
        return truckRepository.findByOwnerId(owner.getId())
                .stream().map(TruckResponse::from).toList();
    }

    // Ownership check used by every owner action: wrong owner = "not found".
    private Truck myTruckOrThrow(Long truckId, String ownerEmail) {
        User owner = currentUser(ownerEmail);
        return truckRepository.findByIdAndOwnerId(truckId, owner.getId()).orElseThrow(() ->
                new IllegalArgumentException("Truck not found for this owner: " + truckId));
    }

    @Transactional
    public TruckResponse updateMyTruck(Long truckId, String ownerEmail, TruckRequest req) {
        Truck truck = myTruckOrThrow(truckId, ownerEmail);
        String regNo = req.registrationNumber().replaceAll("\\s+", "").toUpperCase();
        if (!truck.getRegistrationNumber().equals(regNo)
                && truckRepository.existsByRegistrationNumber(regNo)) {
            throw new IllegalArgumentException("Registration number already exists: " + regNo);
        }
        if (req.permittedPayloadTons().compareTo(req.grossVehicleWeightTons()) >= 0) {
            throw new IllegalArgumentException(
                "Permitted payload must be less than gross vehicle weight");
        }
        applyRequest(truck, req, regNo);
        // Changed details must be checked again before the truck can be booked.
        // Slim registration (Module 3 Part 0): no paper check. A new truck starts
        // UNAVAILABLE; the owner makes it AVAILABLE when it is ready to book.
        truck.setAvailabilityStatus(AvailabilityStatus.UNAVAILABLE);
        return TruckResponse.from(truckRepository.save(truck));
    }

    @Transactional
    public void deleteMyTruck(Long truckId, String ownerEmail) {
        truckRepository.delete(myTruckOrThrow(truckId, ownerEmail));
    }

    @Transactional
    public TruckResponse setAvailability(Long truckId, String ownerEmail, boolean available) {
        Truck truck = myTruckOrThrow(truckId, ownerEmail);
        truck.setAvailabilityStatus(available
                ? AvailabilityStatus.AVAILABLE : AvailabilityStatus.UNAVAILABLE);
        return TruckResponse.from(truckRepository.save(truck));
    }

    // ---- Admin actions ----

    public List<TruckResponse> allTrucks() {
        return truckRepository.findAll().stream().map(TruckResponse::from).toList();
    }

    private Truck truckOrThrow(Long truckId) {
        return truckRepository.findById(truckId).orElseThrow(() ->
                new IllegalArgumentException("Truck not found: " + truckId));
    }

    // The single check later modules must use before creating a booking.
    public boolean isBookable(Long truckId) {
        Truck truck = truckOrThrow(truckId);
        // Slim registration: a truck is bookable when its owner marks it AVAILABLE.
        return truck.getAvailabilityStatus() == AvailabilityStatus.AVAILABLE;
    }
}
