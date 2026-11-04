package com.starkgroup.vms.repository;

import com.starkgroup.vms.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    boolean existsByNormalizedNumber(String normalizedNumber);

    boolean existsByNormalizedNumberAndIdNot(String normalizedNumber, Long id);
}
