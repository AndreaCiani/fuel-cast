package com.fuelcast.station.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A station near a point, with its current price for the requested fuel. */
public record NearbyStation(
        long id,
        String nome,
        String bandiera,
        String indirizzo,
        String comune,
        String provincia,
        double latitude,
        double longitude,
        double distanceMeters,
        String fuelType,
        boolean self,
        BigDecimal price,
        LocalDate observedAt) {
}
