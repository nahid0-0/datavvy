package com.starkgroup.vms.service;

import com.starkgroup.vms.dto.PageResponse;
import com.starkgroup.vms.dto.VehicleNumberAvailabilityResponse;
import com.starkgroup.vms.dto.VehicleRequest;
import com.starkgroup.vms.dto.VehicleResponse;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import org.springframework.data.domain.Pageable;

public interface VehicleService {

    PageResponse<VehicleResponse> findAll(String search, VehicleType type, VehicleStatus status, Pageable pageable);

    VehicleResponse findById(Long id);

    VehicleResponse create(VehicleRequest request);

    VehicleResponse update(Long id, VehicleRequest request);

    void delete(Long id);

    VehicleNumberAvailabilityResponse checkAvailability(String vehicleNumber, Long excludeId);
}
