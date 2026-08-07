package com.fuelcast.price.repository;

import com.fuelcast.price.model.PriceObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

    @Query("select max(p.observedAt) from PriceObservation p")
    LocalDate findMaxObservedAt();

    @Query("select count(distinct p.observedAt) from PriceObservation p")
    long countDistinctObservedDays();
}
