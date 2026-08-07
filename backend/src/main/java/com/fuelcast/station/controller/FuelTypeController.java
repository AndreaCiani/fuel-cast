package com.fuelcast.station.controller;

import com.fuelcast.station.repository.StationQueryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Distinct fuel types seen recently — feeds the frontend's filter dropdown. */
@RestController
@RequestMapping("/api/fuel-types")
public class FuelTypeController {

    private final StationQueryRepository queries;

    public FuelTypeController(StationQueryRepository queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<String> fuelTypes() {
        return queries.distinctFuelTypes(LocalDate.now().minusDays(60));
    }
}
