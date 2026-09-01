package com.fuelcast.manager.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read models for the manager dashboard. */
public final class DashboardDtos {

    private DashboardDtos() { }

    public record ClaimedStation(
            long stationId, String nome, String bandiera, String comune, String provincia,
            Double latitude, Double longitude) { }

    /** Where the manager's station sits among local competitors for a fuel. */
    public record Ranking(
            String fuelType, boolean self, int radiusMeters,
            BigDecimal myPrice, LocalDate myObservedAt,
            int rank, int total, int cheaperThanMe, int dearerThanMe,
            BigDecimal localMin, BigDecimal localMedian, BigDecimal localMax) { }

    /** A competitor price change (the in-app "alert" feed). */
    public record CompetitorChange(
            long stationId, String nome, String bandiera,
            LocalDate date, BigDecimal previousPrice, BigDecimal newPrice, BigDecimal delta) { }

    /** Price-movement activity per station — a proxy for local price leadership. */
    public record Mover(
            long stationId, String nome, String bandiera,
            int changes, BigDecimal avgAbsDelta, LocalDate lastChange, boolean isMine) { }
}
