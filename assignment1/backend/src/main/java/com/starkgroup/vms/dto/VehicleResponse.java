package com.starkgroup.vms.dto;

import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;

import java.time.Instant;

public record VehicleResponse(
        Long id,
        String vehicleNumber,
        String make,
        String model,
        VehicleType type,
        Integer manufactureYear,
        String color,
        String ownerName,
        VehicleStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
