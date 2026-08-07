package com.fuelcast.station.controller;

import com.fuelcast.station.dto.FuelStat;
import com.fuelcast.station.dto.NearbyStation;
import com.fuelcast.station.dto.StationDetail;
import com.fuelcast.station.model.Station;
import com.fuelcast.station.repository.StationQueryRepository;
import com.fuelcast.station.repository.StationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/**
 * Consumer read API. Radius and limit are capped server-side: an unbounded
 * radius/limit is an easy denial-of-service against the spatial queries.
 */
@RestController
@RequestMapping("/api/stations")
public class StationController {

    private static final int RADIUS_DEFAULT = 5_000;
    private static final int RADIUS_MAX = 25_000;
    private static final int LIMIT_DEFAULT = 50;
    private static final int LIMIT_MAX = 200;
    private static final int WINDOW_DEFAULT = 90;
    private static final int WINDOW_MIN = 7;
    private static final int WINDOW_MAX = 730;

    private final StationRepository stations;
    private final StationQueryRepository queries;

    public StationController(StationRepository stations, StationQueryRepository queries) {
        this.stations = stations;
        this.queries = queries;
    }

    @GetMapping("/nearby")
    public List<NearbyStation> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam String fuelType,
            @RequestParam(defaultValue = "true") boolean self,
            @RequestParam(defaultValue = "" + RADIUS_DEFAULT) int radiusMeters,
            @RequestParam(defaultValue = "" + LIMIT_DEFAULT) int limit) {
        requireCoords(lat, lon);
        if (fuelType == null || fuelType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fuelType is required");
        }
        int radius = clamp(radiusMeters, 100, RADIUS_MAX);
        int lim = clamp(limit, 1, LIMIT_MAX);
        return queries.findNearby(lat, lon, fuelType.trim(), self, radius, lim);
    }

    @GetMapping("/{id}")
    public StationDetail detail(
            @PathVariable long id,
            @RequestParam(defaultValue = "" + WINDOW_DEFAULT) int windowDays) {
        Station s = stations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown station " + id));
        int window = clamp(windowDays, WINDOW_MIN, WINDOW_MAX);
        List<FuelStat> fuels = queries.findFuelStats(id, LocalDate.now().minusDays(window));
        return new StationDetail(
                s.getId(), s.getNome(), s.getBandiera(), s.getTipoImpianto(),
                s.getIndirizzo(), s.getComune(), s.getProvincia(),
                s.getLatitude(), s.getLongitude(), window, fuels);
    }

    private static void requireCoords(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lon out of range");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
