package com.starkgroup.vms.dto;

public record VehicleNumberAvailabilityResponse(
        String vehicleNumber,
        boolean available
) {
}
