package com.fuelcast.station;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.ingestion.PriceWriter;
import com.fuelcast.ingestion.StationWriter;
import com.fuelcast.station.dto.FuelStat;
import com.fuelcast.station.dto.NearbyStation;
import com.fuelcast.station.repository.StationQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Geo API against real PostGIS: radius filtering + distance ordering, and the
 * historical percentile math with deterministic values.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StationApiTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired StationWriter stationWriter;
    @Autowired PriceWriter priceWriter;
    @Autowired StationQueryRepository queries;

    @BeforeEach
    void seed() {
        stationWriter.upsertAll(List.of(
                new StationRow(1L, "G1", "Agip Eni", "Stradale", "Roma Centro", "Via 1", "Roma", "RM", 41.9028, 12.4964),
                new StationRow(2L, "G2", "Q8", "Stradale", "Roma Nord", "Via 2", "Roma", "RM", 41.9128, 12.5064),
                new StationRow(3L, "G3", "IP", "Stradale", "Milano", "Via 3", "Milano", "MI", 45.4642, 9.1900)));
        Set<Long> known = Set.of(1L, 2L, 3L);

        // Station 1, Benzina self: window [1,2,3,5] then latest (most recent date) = 4.
        priceWriter.insertAll(List.of(
                price(1, "Benzina", true, "1.00", "2024-01-01"),
                price(1, "Benzina", true, "2.00", "2024-01-02"),
                price(1, "Benzina", true, "3.00", "2024-01-03"),
                price(1, "Benzina", true, "5.00", "2024-01-05"),
                price(1, "Benzina", true, "4.00", "2024-01-06"),
                price(2, "Benzina", true, "2.00", "2024-01-06"),
                price(3, "Benzina", true, "9.99", "2024-01-06")), known);
    }

    @Test
    void nearbyFiltersByRadiusAndOrdersByDistance() {
        List<NearbyStation> result =
                queries.findNearby(41.9028, 12.4964, "Benzina", true, 5_000, 50, LocalDate.of(2023, 1, 1));

        // Station 3 (Milan, ~475 km) is outside the 5 km radius.
        assertThat(result).extracting(NearbyStation::id).containsExactly(1L, 2L);
        assertThat(result.get(0).distanceMeters()).isLessThan(result.get(1).distanceMeters());
        assertThat(result.get(0).price()).isEqualByComparingTo("4.00");
        assertThat(result.get(0).fuelType()).isEqualTo("Benzina");
    }

    @Test
    void fuelStatsComputePercentileOverWindow() {
        List<FuelStat> stats = queries.findFuelStats(1L, LocalDate.of(2023, 12, 31));

        assertThat(stats).hasSize(1);
        FuelStat s = stats.get(0);
        assertThat(s.fuelType()).isEqualTo("Benzina");
        assertThat(s.self()).isTrue();
        assertThat(s.observations()).isEqualTo(5);
        assertThat(s.latestPrice()).isEqualByComparingTo("4.00");
        assertThat(s.min()).isEqualByComparingTo("1.00");
        assertThat(s.max()).isEqualByComparingTo("5.00");
        assertThat(s.median()).isEqualByComparingTo("3.00");
        // latest (4) is >= four of the five values {1,2,3,4} -> 80%.
        assertThat(s.pricierThanPct()).isEqualTo(80);
    }

    private static PriceRow price(long stationId, String fuel, boolean self, String price, String date) {
        return new PriceRow(stationId, fuel, self, new BigDecimal(price), LocalDate.parse(date), null);
    }
}
