package com.fuelcast;

import com.fuelcast.ingestion.IngestionService;
import com.fuelcast.price.repository.PriceObservationRepository;
import com.fuelcast.station.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end ingestion test against a real PostGIS database (Testcontainers).
 * Verifies the pipeline populates the tables and, crucially, that re-running the
 * same snapshot is idempotent — the property the daily/backfill design relies on.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IngestionServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired IngestionService ingestionService;
    @Autowired StationRepository stationRepository;
    @Autowired PriceObservationRepository priceRepository;

    @Test
    void ingestsSampleDataAndIsIdempotent() throws IOException {
        String anagrafica = read("/sample/anagrafica_sample.csv");
        String prezzo = read("/sample/prezzo_sample.csv");

        ingestionService.ingest(anagrafica, prezzo, "TEST");

        // 3 stations; 4 prices (the row for unknown station 999 is skipped).
        assertThat(stationRepository.count()).isEqualTo(3);
        assertThat(priceRepository.count()).isEqualTo(4);
        assertThat(priceRepository.countDistinctObservedDays()).isEqualTo(1);

        // Re-running the same snapshot must not create duplicates.
        ingestionService.ingest(anagrafica, prezzo, "TEST");
        assertThat(stationRepository.count()).isEqualTo(3);
        assertThat(priceRepository.count()).isEqualTo(4);
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = IngestionServiceTests.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Missing test resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
