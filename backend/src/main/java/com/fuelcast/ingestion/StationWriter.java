package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Bulk upsert of station registry rows via JDBC batch. The {@code location}
 * geometry is built server-side with ST_MakePoint(lon, lat) so no spatial type
 * crosses the Java boundary. Existing rows are refreshed (last_seen_at bumped).
 */
@Component
public class StationWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT = """
        INSERT INTO station (id, gestore, bandiera, tipo_impianto, nome, indirizzo,
                             comune, provincia, latitude, longitude, location,
                             first_seen_at, last_seen_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                CASE WHEN ? IS NULL OR ? IS NULL THEN NULL
                     ELSE ST_SetSRID(ST_MakePoint(?, ?), 4326) END,
                now(), now(), now())
        ON CONFLICT (id) DO UPDATE SET
            gestore       = EXCLUDED.gestore,
            bandiera      = EXCLUDED.bandiera,
            tipo_impianto = EXCLUDED.tipo_impianto,
            nome          = EXCLUDED.nome,
            indirizzo     = EXCLUDED.indirizzo,
            comune        = EXCLUDED.comune,
            provincia     = EXCLUDED.provincia,
            latitude      = EXCLUDED.latitude,
            longitude     = EXCLUDED.longitude,
            location      = EXCLUDED.location,
            last_seen_at  = now(),
            updated_at    = now()
        """;

    private final JdbcTemplate jdbc;

    public StationWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return number of station rows written (upserted). */
    public int upsertAll(List<StationRow> rows) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<StationRow> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    StationRow r = batch.get(i);
                    ps.setLong(1, r.id());
                    ps.setString(2, r.gestore());
                    ps.setString(3, r.bandiera());
                    ps.setString(4, r.tipoImpianto());
                    ps.setString(5, r.nome());
                    ps.setString(6, r.indirizzo());
                    ps.setString(7, r.comune());
                    ps.setString(8, r.provincia());
                    setNullableDouble(ps, 9, r.latitude());
                    setNullableDouble(ps, 10, r.longitude());
                    // CASE null-check operands, then ST_MakePoint(lon, lat)
                    setNullableDouble(ps, 11, r.latitude());
                    setNullableDouble(ps, 12, r.longitude());
                    setNullableDouble(ps, 13, r.longitude());
                    setNullableDouble(ps, 14, r.latitude());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
        return rows.size();
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.DOUBLE);
        } else {
            ps.setDouble(idx, value);
        }
    }
}
