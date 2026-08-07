package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.price.repository.PriceObservationRepository;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the archive backfill against a real PostGIS DB using a tar.gz built
 * in memory — covering mixed separators (pipe vs semicolon), path-agnostic file
 * matching, date-from-filename, current-station filtering, and idempotency.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BackfillServiceTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired BackfillService backfillService;
    @Autowired StationWriter stationWriter;
    @Autowired PriceObservationRepository prices;

    @Test
    void backfillsQuarterFilteringUnknownStationsAndIsIdempotent() throws IOException {
        // Only stations 1 and 2 exist in the current registry (not 999).
        stationWriter.upsertAll(List.of(
                new StationRow(1L, "G1", "Agip Eni", "Stradale", "Uno", "Via 1", "Roma", "RM", 41.9, 12.5),
                new StationRow(2L, "G2", "Q8", "Stradale", "Due", "Via 2", "Milano", "MI", 45.5, 9.2)));
        Set<Long> knownIds = new HashSet<>(List.of(1L, 2L));

        byte[] targz = buildQuarterTarGz();

        // Pipe file (2 valid + 1 unknown station) + semicolon file (1 valid) => 3 inserted, 2 days.
        var r = backfillService.processQuarterStream(new Quarter(2024, 3),
                new ByteArrayInputStream(targz), knownIds);
        assertThat(r.days()).isEqualTo(2);
        assertThat(prices.count()).isEqualTo(3);
        assertThat(prices.countDistinctObservedDays()).isEqualTo(2);
        assertThat(prices.findMaxObservedAt()).isEqualTo(LocalDate.of(2024, 7, 2));

        // Re-running the same archive inserts nothing new.
        backfillService.processQuarterStream(new Quarter(2024, 3),
                new ByteArrayInputStream(targz), knownIds);
        assertThat(prices.count()).isEqualTo(3);
    }

    private static byte[] buildQuarterTarGz() throws IOException {
        // Pipe-separated (recent format), directory "2024_3_tr/".
        String pipeDay = """
                Estrazione del 2024-07-01
                idImpianto|descCarburante|prezzo|isSelf|dtComu
                1|Benzina|1.900|1|30/06/2024 20:30:07
                2|Gasolio|1.800|0|30/06/2024 20:30:07
                999|Benzina|1.999|1|30/06/2024 20:30:07
                """;
        // Semicolon-separated (historical format), different directory layout.
        String semiDay = """
                Estrazione del 2024-07-02
                idImpianto;descCarburante;prezzo;isSelf;dtComu
                1;Benzina;1.910;1;01/07/2024 20:30:07
                """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GZIPOutputStream(baos))) {
            addEntry(tar, "2024_3_tr/prezzo_alle_8-20240701.csv", pipeDay);
            addEntry(tar, "ftproot/osservaprezzi/copied/prezzo_alle_8-20240702.csv", semiDay);
            addEntry(tar, "2024_3_tr/README.txt", "not a price file, must be ignored");
        }
        return baos.toByteArray();
    }

    private static void addEntry(TarArchiveOutputStream tar, String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}
