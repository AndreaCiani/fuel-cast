package com.fuelcast.station.controller;

import com.fuelcast.station.TrendService;
import com.fuelcast.station.dto.LocalTrend;
import com.fuelcast.station.dto.LocalTrend.TrendPoint;
import com.fuelcast.station.repository.StationQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** "Fill up now or wait?" — local price trend for a fuel within a radius. */
@RestController
@RequestMapping("/api/trend")
public class TrendController {

    private static final int RADIUS_DEFAULT = 5_000;
    private static final int RADIUS_MAX = 25_000;
    private static final int DAYS_DEFAULT = 60;
    private static final int DAYS_MIN = 14;
    private static final int DAYS_MAX = 365;

    private final StationQueryRepository queries;
    private final TrendService trend;

    public TrendController(StationQueryRepository queries, TrendService trend) {
        this.queries = queries;
        this.trend = trend;
    }

    @GetMapping("/local")
    public LocalTrend local(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam String fuelType,
            @RequestParam(defaultValue = "true") boolean self,
            @RequestParam(defaultValue = "" + RADIUS_DEFAULT) int radiusMeters,
            @RequestParam(defaultValue = "" + DAYS_DEFAULT) int days) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lon out of range");
        }
        if (fuelType == null || fuelType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fuelType is required");
        }
        int radius = clamp(radiusMeters, 100, RADIUS_MAX);
        int window = clamp(days, DAYS_MIN, DAYS_MAX);
        List<TrendPoint> points =
                queries.localDailyAverages(lat, lon, fuelType.trim(), self, radius, LocalDate.now().minusDays(window));
        return trend.build(fuelType.trim(), self, radius, window, points);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
