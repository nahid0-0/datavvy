package com.starkgroup.vms.mapper;

import com.starkgroup.vms.dto.VehicleRequest;
import com.starkgroup.vms.dto.VehicleResponse;
import com.starkgroup.vms.entity.Vehicle;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.util.VehicleNumberNormalizer;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public Vehicle toEntity(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        applyRequest(request, vehicle);
        vehicle.setStatus(request.status() != null ? request.status() : VehicleStatus.ACTIVE);
        return vehicle;
    }

    public void updateEntity(VehicleRequest request, Vehicle vehicle) {
        applyRequest(request, vehicle);
        if (request.status() != null) {
            vehicle.setStatus(request.status());
        }
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getType(),
                vehicle.getManufactureYear(),
                vehicle.getColor(),
                vehicle.getOwnerName(),
                vehicle.getStatus(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt(),
                vehicle.getVersion());
    }

    private void applyRequest(VehicleRequest request, Vehicle vehicle) {
        vehicle.setVehicleNumber(VehicleNumberNormalizer.toDisplay(request.vehicleNumber()));
        vehicle.setNormalizedNumber(VehicleNumberNormalizer.toKey(request.vehicleNumber()));
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setType(request.type());
        vehicle.setManufactureYear(request.manufactureYear());
        vehicle.setColor(request.color());
        vehicle.setOwnerName(request.ownerName());
    }
}
