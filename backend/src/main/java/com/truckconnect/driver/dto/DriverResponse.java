package com.truckconnect.driver.dto;

import com.truckconnect.driver.Driver;

public record DriverResponse(
        Long id, String name, String phone,
        String licenceNumber, boolean active, Long ownerId) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(),
                d.getLicenceNumber(), d.isActive(), d.getOwner().getId());
    }
}
