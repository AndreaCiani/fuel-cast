package com.fuelcast.station.repository;

import com.fuelcast.station.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, Long> {

    /** All known station ids, used to filter out price rows for unknown stations. */
    @Query("select s.id from Station s")
    List<Long> findAllIds();
}
