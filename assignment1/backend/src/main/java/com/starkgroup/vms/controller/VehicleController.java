package com.starkgroup.vms.controller;

import com.starkgroup.vms.dto.PageResponse;
import com.starkgroup.vms.dto.VehicleNumberAvailabilityResponse;
import com.starkgroup.vms.dto.VehicleRequest;
import com.starkgroup.vms.dto.VehicleResponse;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import com.starkgroup.vms.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping(path = "/api/v1/vehicles", produces = "application/json")
@Tag(name = "Vehicles", description = "Register, list, edit and delete vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    @Operation(summary = "List vehicles",
            description = "Paginated list with optional free-text search and type/status filters. "
                    + "Sort example: sort=vehicleNumber,asc")
    public PageResponse<VehicleResponse> list(
            @Parameter(description = "Matches vehicle number, make, model or owner (case-insensitive)")
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) VehicleType type,
            @RequestParam(required = false) VehicleStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return vehicleService.findAll(search, type, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a vehicle by id")
    public VehicleResponse get(@PathVariable @Positive Long id) {
        return vehicleService.findById(id);
    }

    @GetMapping("/availability")
    @Operation(summary = "Check whether a vehicle number is free",
            description = "Comparison ignores case, spaces and hyphens. "
                    + "Pass excludeId when editing so the vehicle's own number is not reported as taken.")
    public VehicleNumberAvailabilityResponse checkAvailability(
            @RequestParam @NotBlank @Size(max = 32) String vehicleNumber,
            @RequestParam(required = false) @Positive Long excludeId) {
        return vehicleService.checkAvailability(vehicleNumber, excludeId);
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Register a new vehicle")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse created = vehicleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    @Operation(summary = "Update an existing vehicle")
    public VehicleResponse update(@PathVariable @Positive Long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a vehicle")
    public void delete(@PathVariable @Positive Long id) {
        vehicleService.delete(id);
    }
}
