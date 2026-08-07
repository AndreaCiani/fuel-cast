package com.fuelcast.station.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Local price trend for a fuel within a radius, powering the "fill up now or
 * wait?" signal.
 *
 * @param direction   RISING | FALLING | STABLE | INSUFFICIENT
 * @param deltaPerLitre recentAvg − previousAvg (€/L); >0 means prices are climbing
 */
public record LocalTrend(
        String fuelType,
        boolean self,
        int radiusMeters,
        int days,
        String direction,
        BigDecimal recentAvg,
        BigDecimal previousAvg,
        BigDecimal deltaPerLitre,
        List<TrendPoint> points) {

    public record TrendPoint(LocalDate date, BigDecimal avgPrice, int stations) { }
}
