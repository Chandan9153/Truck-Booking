package com.truckconnect.truck.dto;

import com.truckconnect.truck.TruckCategory;
import java.math.BigDecimal;

public record TruckCategoryResponse(
        Long id, String name, int tyreCount,
        BigDecimal targetPayloadTons, String description, boolean active
) {
    public static TruckCategoryResponse from(TruckCategory c) {
        return new TruckCategoryResponse(c.getId(), c.getName(), c.getTyreCount(),
                c.getTargetPayloadTons(), c.getDescription(), c.isActive());
    }
}
