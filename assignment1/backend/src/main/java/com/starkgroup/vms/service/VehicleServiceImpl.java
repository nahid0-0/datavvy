package com.starkgroup.vms.service;

import com.starkgroup.vms.dto.PageResponse;
import com.starkgroup.vms.dto.VehicleNumberAvailabilityResponse;
import com.starkgroup.vms.dto.VehicleRequest;
import com.starkgroup.vms.dto.VehicleResponse;
import com.starkgroup.vms.entity.Vehicle;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import com.starkgroup.vms.exception.DuplicateVehicleNumberException;
import com.starkgroup.vms.exception.InvalidRequestException;
import com.starkgroup.vms.exception.ResourceNotFoundException;
import com.starkgroup.vms.exception.VersionConflictException;
import com.starkgroup.vms.mapper.VehicleMapper;
import com.starkgroup.vms.repository.VehicleRepository;
import com.starkgroup.vms.repository.VehicleSpecifications;
import com.starkgroup.vms.util.VehicleNumberNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);
    private static final String RESOURCE = "Vehicle";
    private static final String UNIQUE_NUMBER_CONSTRAINT = "uk_vehicles_normalized_number";

    /** Public API sort keys mapped to entity properties. Anything else is rejected. */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "id",
            "vehicleNumber", "vehicleNumber",
            "make", "make",
            "model", "model",
            "type", "type",
            "manufactureYear", "manufactureYear",
            "status", "status",
            "ownerName", "ownerName",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final VehicleRepository repository;
    private final VehicleMapper mapper;

    public VehicleServiceImpl(VehicleRepository repository, VehicleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PageResponse<VehicleResponse> findAll(String search, VehicleType type, VehicleStatus status,
                                                 Pageable pageable) {
        List<Specification<Vehicle>> specs = new ArrayList<>();
        addIfPresent(specs, VehicleSpecifications.matchesSearch(search));
        addIfPresent(specs, VehicleSpecifications.hasType(type));
        addIfPresent(specs, VehicleSpecifications.hasStatus(status));

        Pageable safePageable = sanitizeSort(pageable);
        var page = repository.findAll(Specification.allOf(specs), safePageable);
        return PageResponse.from(page, mapper::toResponse);
    }

    @Override
    public VehicleResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        String key = VehicleNumberNormalizer.toKey(request.vehicleNumber());
        if (repository.existsByNormalizedNumber(key)) {
            throw new DuplicateVehicleNumberException(VehicleNumberNormalizer.toDisplay(request.vehicleNumber()));
        }
        Vehicle saved = saveAndFlush(mapper.toEntity(request));
        log.info("Registered vehicle id={} number={}", saved.getId(), saved.getVehicleNumber());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = getOrThrow(id);

        if (request.version() != null && !Objects.equals(request.version(), vehicle.getVersion())) {
            throw new VersionConflictException(
                    "This vehicle was modified by someone else. Reload it and apply your changes again.");
        }

        String key = VehicleNumberNormalizer.toKey(request.vehicleNumber());
        if (repository.existsByNormalizedNumberAndIdNot(key, id)) {
            throw new DuplicateVehicleNumberException(VehicleNumberNormalizer.toDisplay(request.vehicleNumber()));
        }

        mapper.updateEntity(request, vehicle);
        Vehicle saved = saveAndFlush(vehicle);
        log.info("Updated vehicle id={} number={}", saved.getId(), saved.getVehicleNumber());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = getOrThrow(id);
        repository.delete(vehicle);
        log.info("Deleted vehicle id={} number={}", id, vehicle.getVehicleNumber());
    }

    @Override
    public VehicleNumberAvailabilityResponse checkAvailability(String vehicleNumber, Long excludeId) {
        String key = VehicleNumberNormalizer.toKey(vehicleNumber);
        String display = VehicleNumberNormalizer.toDisplay(vehicleNumber);
        if (key.isEmpty()) {
            throw new InvalidRequestException("vehicleNumber", "must contain at least one letter or digit");
        }
        boolean taken = excludeId == null
                ? repository.existsByNormalizedNumber(key)
                : repository.existsByNormalizedNumberAndIdNot(key, excludeId);
        return new VehicleNumberAvailabilityResponse(display, !taken);
    }

    private Vehicle getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    /**
     * Flushes immediately so a unique-constraint violation caused by a concurrent insert
     * (which slipped past the exists-check) surfaces here and is translated to a 409.
     */
    private Vehicle saveAndFlush(Vehicle vehicle) {
        try {
            return repository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueNumberViolation(ex)) {
                throw new DuplicateVehicleNumberException(vehicle.getVehicleNumber());
            }
            throw ex;
        }
    }

    private static boolean isUniqueNumberViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(UNIQUE_NUMBER_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static Pageable sanitizeSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String property = SORTABLE_FIELDS.get(order.getProperty());
            if (property == null) {
                throw new InvalidRequestException("sort",
                        "Unsupported sort field '" + order.getProperty() + "'. Allowed: "
                                + String.join(", ", SORTABLE_FIELDS.keySet().stream().sorted().toList()));
            }
            Sort.Order mapped = new Sort.Order(order.getDirection(), property);
            orders.add(isText(property) ? mapped.ignoreCase() : mapped);
        }
        // Tie-breaker guarantees deterministic paging when the sort key has duplicates.
        if (orders.stream().noneMatch(o -> o.getProperty().equals("id"))) {
            orders.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private static boolean isText(String property) {
        return switch (property) {
            case "vehicleNumber", "make", "model", "ownerName" -> true;
            default -> false;
        };
    }

    private static <T> void addIfPresent(List<Specification<T>> specs, Specification<T> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
