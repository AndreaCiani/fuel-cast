package com.fuelcast.station;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.ingestion.PriceWriter;
import com.fuelcast.ingestion.StationWriter;
import com.fuelcast.station.dto.LocalTrend;
import com.fuelcast.station.dto.LocalTrend.TrendPoint;
import com.fuelcast.station.repository.StationQueryRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Local price trend against real PostGIS: a rising series is detected as RISING. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TrendServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired StationWriter stationWriter;
    @Autowired PriceWriter priceWriter;
    @Autowired StationQueryRepository queries;
    @Autowired TrendService trendService;

    @Test
    void detectsRisingLocalTrend() {
        stationWriter.upsertAll(List.of(
                new StationRow(1L, "G1", "Agip Eni", "Stradale", "Roma A", "Via 1", "Roma", "RM", 41.9028, 12.4964),
                new StationRow(2L, "G2", "Q8", "Stradale", "Roma B", "Via 2", "Roma", "RM", 41.9050, 12.4990)));
        Set<Long> known = Set.of(1L, 2L);

        // 20 consecutive days: first 13 at 1.700, last 7 at 1.800 -> rising.
        LocalDate today = LocalDate.now();
        List<PriceRow> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            LocalDate date = today.minusDays(19 - i);
            BigDecimal price = new BigDecimal(i < 13 ? "1.700" : "1.800");
            rows.add(new PriceRow(1L, "Benzina", true, price, date, null));
            rows.add(new PriceRow(2L, "Benzina", true, price, date, null));
        }
        priceWriter.insertAll(rows, known);

        List<TrendPoint> points =
                queries.localDailyAverages(41.9028, 12.4964, "Benzina", true, 5_000, today.minusDays(60));
        assertThat(points).hasSize(20);

        LocalTrend t = trendService.build("Benzina", true, 5_000, 60, points);
        assertThat(t.direction()).isEqualTo("RISING");
        assertThat(t.deltaPerLitre()).isGreaterThan(BigDecimal.ZERO);
        assertThat(t.recentAvg()).isEqualByComparingTo("1.800");
        assertThat(t.previousAvg()).isEqualByComparingTo("1.700");
    }
}
