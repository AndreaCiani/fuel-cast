package com.fuelcast.manager;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.ingestion.PriceWriter;
import com.fuelcast.ingestion.StationWriter;
import com.fuelcast.manager.dto.DashboardDtos.CompetitorChange;
import com.fuelcast.manager.dto.DashboardDtos.Mover;
import com.fuelcast.manager.dto.DashboardDtos.Ranking;
import com.fuelcast.manager.repository.ManagerQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Side B: competitor analytics SQL + the public-A / protected-B security split. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ManagerApiTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @Autowired StationWriter stationWriter;
    @Autowired PriceWriter priceWriter;
    @Autowired ManagerQueryRepository analytics;
    @Autowired MockMvc mvc;

    private static final LocalDate D = LocalDate.of(2026, 6, 30);

    @BeforeEach
    void seed() {
        stationWriter.upsertAll(List.of(
                new StationRow(1L, "Io", "Agip Eni", "Stradale", "Mia", "Via 1", "Roma", "RM", 41.9028, 12.4964),
                new StationRow(2L, "C2", "Q8", "Stradale", "Comp2", "Via 2", "Roma", "RM", 41.9050, 12.4990),
                new StationRow(3L, "C3", "IP", "Stradale", "Comp3", "Via 3", "Roma", "RM", 41.9000, 12.5000),
                new StationRow(4L, "Far", "Esso", "Stradale", "Milano", "Via 4", "Milano", "MI", 45.4642, 9.1900)));
        Set<Long> known = Set.of(1L, 2L, 3L, 4L);
        priceWriter.insertAll(List.of(
                price(1, "1.880", D.minusDays(1)), price(1, "1.900", D),   // mine, with a change
                price(2, "1.850", D),                                       // competitor, cheaper, no change
                price(3, "1.900", D.minusDays(1)), price(3, "1.950", D),   // competitor, dearer, with a change
                price(4, "1.700", D)),                                      // far, excluded by radius
                known);
    }

    @Test
    void rankingPlacesStationAmongLocalCompetitors() {
        Ranking r = analytics.ranking(1L, "Benzina", true, 5_000, D.minusDays(14));
        assertThat(r.myPrice()).isEqualByComparingTo("1.900");
        assertThat(r.total()).isEqualTo(3);          // station 4 excluded by radius
        assertThat(r.cheaperThanMe()).isEqualTo(1);  // station 2
        assertThat(r.dearerThanMe()).isEqualTo(1);   // station 3
        assertThat(r.rank()).isEqualTo(2);
        assertThat(r.localMin()).isEqualByComparingTo("1.850");
        assertThat(r.localMax()).isEqualByComparingTo("1.950");
    }

    @Test
    void competitorChangesAndMovers() {
        List<CompetitorChange> changes = analytics.competitorChanges(1L, "Benzina", true, 5_000, D.minusDays(30), 50);
        assertThat(changes).extracting(CompetitorChange::stationId).containsExactly(3L); // only comp 3 changed

        List<Mover> movers = analytics.movers(1L, "Benzina", true, 5_000, D.minusDays(30), 50);
        assertThat(movers).extracting(Mover::stationId).containsExactlyInAnyOrder(1L, 3L);
        assertThat(movers.stream().filter(Mover::isMine)).extracting(Mover::stationId).containsExactly(1L);
    }

    @Test
    void sideAPublicButSideBProtected() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk());
        mvc.perform(get("/api/manager/stations")).andExpect(status().isUnauthorized());
    }

    private static PriceRow price(long stationId, String price, LocalDate date) {
        return new PriceRow(stationId, "Benzina", true, new BigDecimal(price), date, null);
    }
}
