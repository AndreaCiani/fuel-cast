package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure parser tests (no DB): separator detection and out-of-range price filtering. */
class MimitCsvParserTests {

    private final MimitCsvParser parser = new MimitCsvParser();

    @Test
    void skipsOutOfRangePrices() {
        String csv = """
                Estrazione del 2026-01-15
                idImpianto|descCarburante|prezzo|isSelf|dtComu
                1|Benzina|1.899|1|14/01/2026 20:30:07
                2|Gasolio|1800.000|0|14/01/2026 20:30:07
                3|GPL|0.000|0|14/01/2026 20:30:07
                """;
        List<PriceRow> rows = parser.parsePrices(csv, LocalDate.of(2026, 1, 15));

        // Only station 1 survives: 1800 overflows NUMERIC(6,3), 0 is not a real price.
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).stationId()).isEqualTo(1L);
        assertThat(rows.get(0).price()).isEqualByComparingTo("1.899");
    }

    @Test
    void autoDetectsSemicolonSeparator() {
        assertThat(MimitCsvParser.detectSeparator("idImpianto;descCarburante;prezzo;isSelf;dtComu")).isEqualTo(";");
        assertThat(MimitCsvParser.detectSeparator("idImpianto|descCarburante|prezzo|isSelf|dtComu")).isEqualTo("|");
    }
}
