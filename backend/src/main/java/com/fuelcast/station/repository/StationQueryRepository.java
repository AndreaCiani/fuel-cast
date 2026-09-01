package com.fuelcast.station.repository;

import com.fuelcast.station.dto.FuelStat;
import com.fuelcast.station.dto.LocalTrend.TrendPoint;
import com.fuelcast.station.dto.NearbyStation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Native geo/analytics read queries. Kept separate from the JPA
 * {@link StationRepository} because these are hand-written PostGIS/window SQL,
 * not entity CRUD.
 */
@Repository
public class StationQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public StationQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String NEARBY = """
        SELECT s.id, s.nome, s.bandiera, s.indirizzo, s.comune, s.provincia,
               s.latitude, s.longitude,
               ST_Distance(s.location::geography,
                           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS distance_m,
               p.is_self, p.price, p.observed_at
        FROM station s
        JOIN LATERAL (
            SELECT is_self, price, observed_at
            FROM price_observation po
            WHERE po.station_id = s.id
              AND lower(po.fuel_type) = lower(:fuel)
              AND po.is_self = :self
              AND po.observed_at >= :freshnessSince
            ORDER BY po.observed_at DESC
            LIMIT 1
        ) p ON true
        WHERE s.location IS NOT NULL
          AND ST_DWithin(s.location::geography,
                         ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
        ORDER BY distance_m
        LIMIT :limit
        """;

    public List<NearbyStation> findNearby(double lat, double lon, String fuel, boolean self,
                                          int radiusMeters, int limit, LocalDate freshnessSince) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("lat", lat).addValue("lon", lon)
                .addValue("fuel", fuel).addValue("self", self)
                .addValue("radius", radiusMeters).addValue("limit", limit)
                .addValue("freshnessSince", freshnessSince);
        return jdbc.query(NEARBY, p, (rs, i) -> new NearbyStation(
                rs.getLong("id"), rs.getString("nome"), rs.getString("bandiera"),
                rs.getString("indirizzo"), rs.getString("comune"), rs.getString("provincia"),
                rs.getDouble("latitude"), rs.getDouble("longitude"),
                rs.getDouble("distance_m"), fuel, rs.getBoolean("is_self"),
                rs.getBigDecimal("price"), rs.getObject("observed_at", LocalDate.class)));
    }

    private static final String FUEL_STATS = """
        WITH obs AS (
            SELECT fuel_type, is_self, price, observed_at
            FROM price_observation
            WHERE station_id = :id AND observed_at >= :since
        ),
        latest AS (
            SELECT DISTINCT ON (fuel_type, is_self)
                   fuel_type, is_self, price AS latest_price, observed_at AS latest_date
            FROM obs
            ORDER BY fuel_type, is_self, observed_at DESC
        )
        SELECT o.fuel_type, o.is_self,
               l.latest_price, l.latest_date,
               count(*)                                                        AS n,
               min(o.price)                                                    AS min_price,
               max(o.price)                                                    AS max_price,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY o.price)            AS median_price,
               avg(o.price)                                                    AS avg_price,
               round(100.0 * count(*) FILTER (WHERE o.price <= l.latest_price)
                     / count(*))                                               AS pricier_than_pct
        FROM obs o
        JOIN latest l ON l.fuel_type = o.fuel_type AND l.is_self = o.is_self
        GROUP BY o.fuel_type, o.is_self, l.latest_price, l.latest_date
        ORDER BY o.fuel_type, o.is_self
        """;

    public List<FuelStat> findFuelStats(long stationId, LocalDate since) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", stationId).addValue("since", since);
        return jdbc.query(FUEL_STATS, p, (rs, i) -> new FuelStat(
                rs.getString("fuel_type"), rs.getBoolean("is_self"),
                rs.getBigDecimal("latest_price"), rs.getObject("latest_date", LocalDate.class),
                rs.getLong("n"), rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price"),
                rs.getBigDecimal("median_price"), rs.getBigDecimal("avg_price"),
                rs.getInt("pricier_than_pct")));
    }

    private static final String FUEL_TYPES = """
        SELECT DISTINCT fuel_type
        FROM price_observation
        WHERE observed_at >= :since
        ORDER BY fuel_type
        """;

    public List<String> distinctFuelTypes(LocalDate since) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("since", since);
        return jdbc.queryForList(FUEL_TYPES, p, String.class);
    }

    private static final String LOCAL_DAILY_AVG = """
        SELECT po.observed_at AS d,
               avg(po.price)              AS avg_price,
               count(DISTINCT po.station_id) AS n
        FROM price_observation po
        WHERE po.station_id IN (
                SELECT s.id FROM station s
                WHERE s.location IS NOT NULL
                  AND ST_DWithin(s.location::geography,
                                 ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
              )
          AND lower(po.fuel_type) = lower(:fuel)
          AND po.is_self = :self
          AND po.observed_at >= :since
        GROUP BY po.observed_at
        ORDER BY po.observed_at
        """;

    /** Daily average price across stations within the radius, for the trend signal. */
    public List<TrendPoint> localDailyAverages(double lat, double lon, String fuel, boolean self,
                                               int radiusMeters, LocalDate since) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("lat", lat).addValue("lon", lon)
                .addValue("fuel", fuel).addValue("self", self)
                .addValue("radius", radiusMeters).addValue("since", since);
        return jdbc.query(LOCAL_DAILY_AVG, p, (rs, i) -> new TrendPoint(
                rs.getObject("d", LocalDate.class),
                rs.getBigDecimal("avg_price"),
                rs.getInt("n")));
    }
}
