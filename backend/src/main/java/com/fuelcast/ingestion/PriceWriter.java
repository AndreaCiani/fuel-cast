package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Set;

/**
 * Bulk insert of price observations via JDBC batch. Insertion is idempotent:
 * {@code ON CONFLICT DO NOTHING} on (station_id, fuel_type, is_self, observed_at)
 * means re-running the same day's snapshot inserts nothing new. Rows for stations
 * absent from the registry are skipped to avoid FK violations.
 */
@Component
public class PriceWriter {

    private static final Logger log = LoggerFactory.getLogger(PriceWriter.class);
    private static final int BATCH_SIZE = 2000;

    private static final String INSERT = """
        INSERT INTO price_observation
            (station_id, fuel_type, is_self, price, observed_at, communicated_at, ingested_at)
        VALUES (?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (station_id, fuel_type, is_self, observed_at) DO NOTHING
        """;

    private final JdbcTemplate jdbc;

    public PriceWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param rows      parsed price rows
     * @param knownIds  station ids present in the registry (rows for others are skipped)
     * @return number of rows actually inserted (excludes conflicts and skips)
     */
    public int insertAll(List<PriceRow> rows, Set<Long> knownIds) {
        List<PriceRow> insertable = rows.stream()
                .filter(r -> knownIds.contains(r.stationId()))
                .toList();
        int skipped = rows.size() - insertable.size();
        if (skipped > 0) log.warn("Skipped {} price rows referencing unknown stations", skipped);

        int inserted = 0;
        for (int start = 0; start < insertable.size(); start += BATCH_SIZE) {
            List<PriceRow> batch = insertable.subList(start, Math.min(start + BATCH_SIZE, insertable.size()));
            int[] counts = jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    PriceRow r = batch.get(i);
                    ps.setLong(1, r.stationId());
                    ps.setString(2, r.fuelType());
                    ps.setBoolean(3, r.self());
                    ps.setBigDecimal(4, r.price() == null ? BigDecimal.ZERO : r.price());
                    ps.setObject(5, r.observedAt());
                    if (r.communicatedAt() == null) {
                        ps.setNull(6, Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(6, Timestamp.from(r.communicatedAt()));
                    }
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
            for (int c : counts) if (c > 0) inserted++;
        }
        return inserted;
    }
}
