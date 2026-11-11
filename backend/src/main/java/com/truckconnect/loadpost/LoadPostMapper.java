package com.truckconnect.loadpost;

import com.truckconnect.loadpost.dto.LoadPostResponse;

public final class LoadPostMapper {

    private LoadPostMapper() { }

    public static LoadPostResponse toResponse(LoadPost p,
                                              int trucksBooked) {
        return new LoadPostResponse(
                p.getId(),
                p.getPostedBy().getId(),
                p.getPostedBy().getFullName(),
                p.getGoodsDescription(),
                p.getTotalTons(),
                p.getTrucksNeeded(),
                p.getTonsPerTruck(),
                p.getCategory().getName(),
                p.getCategory().getTyreCount(),
                p.getBodyType() == null ? null : p.getBodyType().name(),
                p.getPickup(),
                p.getDestination(),
                p.getDistanceKm(),
                p.getPickupDate(),
                p.getTollCharges(),
                p.getLoadingCharges(),
                p.getStatus(),
                trucksBooked,
                p.getTrucksNeeded() - trucksBooked,
                p.getCreatedAt());
    }
}
