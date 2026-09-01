package com.fuelcast.manager.controller;

import com.fuelcast.manager.dto.DashboardDtos.ClaimedStation;
import com.fuelcast.manager.dto.DashboardDtos.CompetitorChange;
import com.fuelcast.manager.dto.DashboardDtos.Mover;
import com.fuelcast.manager.dto.DashboardDtos.Ranking;
import com.fuelcast.manager.dto.ManagerDtos.ClaimRequest;
import com.fuelcast.manager.model.ManagerStation;
import com.fuelcast.manager.repository.ManagerQueryRepository;
import com.fuelcast.manager.repository.ManagerStationRepository;
import com.fuelcast.manager.security.CurrentManagerService;
import com.fuelcast.price.repository.PriceObservationRepository;
import com.fuelcast.station.model.Station;
import com.fuelcast.station.repository.StationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Station-manager dashboard API. All endpoints require an authenticated manager. */
@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private static final int RADIUS_DEFAULT = 5_000;
    private static final int RADIUS_MAX = 25_000;
    private static final int WINDOW_DEFAULT = 90;
    private static final int WINDOW_MIN = 14;
    private static final int WINDOW_MAX = 365;
    private static final int FRESHNESS_DAYS = 14;
    private static final int LIMIT_DEFAULT = 50;
    private static final int LIMIT_MAX = 200;

    private final CurrentManagerService currentManager;
    private final ManagerStationRepository links;
    private final StationRepository stations;
    private final PriceObservationRepository prices;
    private final ManagerQueryRepository analytics;

    public ManagerController(CurrentManagerService currentManager, ManagerStationRepository links,
                             StationRepository stations, PriceObservationRepository prices,
                             ManagerQueryRepository analytics) {
        this.currentManager = currentManager;
        this.links = links;
        this.stations = stations;
        this.prices = prices;
        this.analytics = analytics;
    }

    // --- Claimed stations ---

    @GetMapping("/stations")
    public List<ClaimedStation> myStations() {
        Long managerId = currentManager.require().getId();
        return links.findByManagerId(managerId).stream()
                .map(ManagerStation::getStationId)
                .map(stations::findById)
                .flatMap(java.util.Optional::stream)
                .map(ManagerController::toClaimed)
                .toList();
    }

    @PostMapping("/stations")
    @Transactional
    public ResponseEntity<ClaimedStation> claim(@Valid @RequestBody ClaimRequest req) {
        Long managerId = currentManager.require().getId();
        Station station = stations.findById(req.stationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown station " + req.stationId()));
        if (!links.existsByManagerIdAndStationId(managerId, station.getId())) {
            links.save(new ManagerStation(managerId, station.getId()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toClaimed(station));
    }

    @DeleteMapping("/stations/{stationId}")
    @Transactional
    public ResponseEntity<Void> unclaim(@PathVariable long stationId) {
        Long managerId = currentManager.require().getId();
        links.findByManagerIdAndStationId(managerId, stationId).ifPresent(links::delete);
        return ResponseEntity.noContent().build();
    }

    // --- Analytics (ownership-checked) ---

    @GetMapping("/stations/{stationId}/ranking")
    public Ranking ranking(@PathVariable long stationId,
                           @RequestParam String fuelType,
                           @RequestParam(defaultValue = "true") boolean self,
                           @RequestParam(defaultValue = "" + RADIUS_DEFAULT) int radiusMeters) {
        requireOwned(stationId);
        LocalDate since = referenceDate().minusDays(FRESHNESS_DAYS);
        return analytics.ranking(stationId, requireFuel(fuelType), self, clampRadius(radiusMeters), since);
    }

    @GetMapping("/stations/{stationId}/competitors")
    public List<CompetitorChange> competitorChanges(@PathVariable long stationId,
                                                    @RequestParam String fuelType,
                                                    @RequestParam(defaultValue = "true") boolean self,
                                                    @RequestParam(defaultValue = "" + RADIUS_DEFAULT) int radiusMeters,
                                                    @RequestParam(defaultValue = "" + WINDOW_DEFAULT) int days,
                                                    @RequestParam(defaultValue = "" + LIMIT_DEFAULT) int limit) {
        requireOwned(stationId);
        LocalDate since = referenceDate().minusDays(clampWindow(days));
        return analytics.competitorChanges(stationId, requireFuel(fuelType), self,
                clampRadius(radiusMeters), since, clampLimit(limit));
    }

    @GetMapping("/stations/{stationId}/movers")
    public List<Mover> movers(@PathVariable long stationId,
                              @RequestParam String fuelType,
                              @RequestParam(defaultValue = "true") boolean self,
                              @RequestParam(defaultValue = "" + RADIUS_DEFAULT) int radiusMeters,
                              @RequestParam(defaultValue = "" + WINDOW_DEFAULT) int days,
                              @RequestParam(defaultValue = "" + LIMIT_DEFAULT) int limit) {
        requireOwned(stationId);
        LocalDate since = referenceDate().minusDays(clampWindow(days));
        return analytics.movers(stationId, requireFuel(fuelType), self,
                clampRadius(radiusMeters), since, clampLimit(limit));
    }

    // --- helpers ---

    private void requireOwned(long stationId) {
        Long managerId = currentManager.require().getId();
        if (!links.existsByManagerIdAndStationId(managerId, stationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage station " + stationId);
        }
    }

    private LocalDate referenceDate() {
        LocalDate max = prices.findMaxObservedAt();
        return max != null ? max : LocalDate.now();
    }

    private static String requireFuel(String fuelType) {
        if (fuelType == null || fuelType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fuelType is required");
        }
        return fuelType.trim();
    }

    private static int clampRadius(int v) { return Math.max(100, Math.min(RADIUS_MAX, v)); }
    private static int clampWindow(int v) { return Math.max(WINDOW_MIN, Math.min(WINDOW_MAX, v)); }
    private static int clampLimit(int v) { return Math.max(1, Math.min(LIMIT_MAX, v)); }

    private static ClaimedStation toClaimed(Station s) {
        return new ClaimedStation(s.getId(), s.getNome(), s.getBandiera(), s.getComune(),
                s.getProvincia(), s.getLatitude(), s.getLongitude());
    }
}
