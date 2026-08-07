package com.fuelcast.status;

import com.fuelcast.ingestion.IngestRun;
import com.fuelcast.ingestion.IngestRunRepository;
import com.fuelcast.price.repository.PriceObservationRepository;
import com.fuelcast.station.repository.StationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Public, read-only ingestion health view. Doubles as monitoring ("how fresh is
 * the data, how many days of history do we hold?") and as a portfolio signal of
 * operational awareness.
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final StationRepository stations;
    private final PriceObservationRepository prices;
    private final IngestRunRepository runs;

    public StatusController(StationRepository stations, PriceObservationRepository prices, IngestRunRepository runs) {
        this.stations = stations;
        this.prices = prices;
        this.runs = runs;
    }

    public record LastRun(String source, String status, LocalDate snapshotDate,
                          Integer stationsUpserted, Integer pricesInserted,
                          Instant startedAt, Instant finishedAt, String message) { }

    public record StatusResponse(long stationCount, long priceObservationCount,
                                 LocalDate latestObservedDate, long distinctObservedDays,
                                 LastRun lastRun) { }

    @GetMapping
    public StatusResponse status() {
        LastRun last = runs.findTopByOrderByStartedAtDesc()
                .map(r -> new LastRun(r.getSource(), r.getStatus(), r.getSnapshotDate(),
                        r.getStationsUpserted(), r.getPricesInserted(),
                        r.getStartedAt(), r.getFinishedAt(), r.getMessage()))
                .orElse(null);
        return new StatusResponse(
                stations.count(),
                prices.count(),
                prices.findMaxObservedAt(),
                prices.countDistinctObservedDays(),
                last);
    }
}
