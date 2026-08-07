package com.fuelcast.station.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historical stats for one fuel type / mode at a station, over a time window.
 *
 * @param pricierThanPct the latest price is pricier than this % of the window's
 *                       observations (the "pricier than 82% of the last 90 days"
 *                       signal). 0 = cheapest it's been, 100 = most expensive.
 */
public record FuelStat(
        String fuelType,
        boolean self,
        BigDecimal latestPrice,
        LocalDate latestDate,
        long observations,
        BigDecimal min,
        BigDecimal max,
        BigDecimal median,
        BigDecimal avg,
        int pricierThanPct) {
}
