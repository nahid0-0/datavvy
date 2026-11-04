package com.starkgroup.vms.service;

import com.starkgroup.vms.dto.VehicleRequest;
import com.starkgroup.vms.entity.Vehicle;
import com.starkgroup.vms.entity.VehicleStatus;
import com.starkgroup.vms.entity.VehicleType;
import com.starkgroup.vms.exception.DuplicateVehicleNumberException;
import com.starkgroup.vms.exception.InvalidRequestException;
import com.starkgroup.vms.exception.ResourceNotFoundException;
import com.starkgroup.vms.exception.VersionConflictException;
import com.starkgroup.vms.mapper.VehicleMapper;
import com.starkgroup.vms.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository repository;

    private VehicleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VehicleServiceImpl(repository, new VehicleMapper());
    }

    private static VehicleRequest request(String number, Long version) {
        return new VehicleRequest(number, "Toyota", "Corolla", VehicleType.CAR, 2020,
                "White", "Tony Stark", null, version);
    }

    @Test
    void create_rejectsDuplicateUsingNormalizedKey() {
        when(repository.existsByNormalizedNumber("DHA1234")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("dha 12-34", null)))
                .isInstanceOf(DuplicateVehicleNumberException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_translatesUniqueConstraintRaceInto409() {
        when(repository.existsByNormalizedNumber("DHA1234")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"uk_vehicles_normalized_number\"]"));

        assertThatThrownBy(() -> service.create(request("DHA-1234", null)))
                .isInstanceOf(DuplicateVehicleNumberException.class);
    }

    @Test
    void create_rethrowsUnrelatedIntegrityViolations() {
        when(repository.existsByNormalizedNumber(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ck_vehicles_type"));

        assertThatThrownBy(() -> service.create(request("DHA-1234", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void create_defaultsStatusToActive() {
        when(repository.existsByNormalizedNumber(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(request("DHA-1234", null));

        assertThat(response.status()).isEqualTo(VehicleStatus.ACTIVE);
        assertThat(response.vehicleNumber()).isEqualTo("DHA-1234");
    }

    @Test
    void update_missingVehicle_throwsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("DHA-1234", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_staleVersion_throwsConflict() {
        Vehicle existing = new Vehicle();
        existing.setId(1L);
        // version is null on a fresh entity -> any supplied version is stale
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, request("DHA-1234", 3L)))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    void update_numberTakenByAnotherVehicle_throwsDuplicate() {
        Vehicle existing = new Vehicle();
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNormalizedNumberAndIdNot("DHA1234", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("DHA-1234", null)))
                .isInstanceOf(DuplicateVehicleNumberException.class);
    }

    @Test
    void delete_missingVehicle_throwsNotFound() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAll_rejectsUnknownSortField() {
        var pageable = PageRequest.of(0, 20, Sort.by("normalizedNumber"));

        assertThatThrownBy(() -> service.findAll(null, null, null, pageable))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("normalizedNumber");
    }

    @Test
    void checkAvailability_rejectsNumbersWithoutAlphanumerics() {
        assertThatThrownBy(() -> service.checkAvailability("---", null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
