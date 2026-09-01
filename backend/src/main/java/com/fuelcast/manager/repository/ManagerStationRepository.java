package com.fuelcast.manager.repository;

import com.fuelcast.manager.model.ManagerStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagerStationRepository extends JpaRepository<ManagerStation, Long> {
    List<ManagerStation> findByManagerId(Long managerId);
    Optional<ManagerStation> findByManagerIdAndStationId(Long managerId, Long stationId);
    boolean existsByManagerIdAndStationId(Long managerId, Long stationId);
}
