package com.fuelcast.manager.repository;

import com.fuelcast.manager.dto.DashboardDtos.CompetitorChange;
import com.fuelcast.manager.dto.DashboardDtos.Mover;
import com.fuelcast.manager.dto.DashboardDtos.Ranking;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** Native competitor-analytics queries for the manager dashboard. */
@Repository
public class ManagerQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ManagerQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String RANKING = """
        WITH nearby AS (
            SELECT DISTINCT ON (po.station_id) po.station_id, po.price, po.observed_at
            FROM price_observation po
            JOIN station s ON s.id = po.station_id
            WHERE lower(po.fuel_type) = lower(:fuel) AND po.is_self = :self
              AND po.observed_at >= :since
              AND ST_DWithin(s.location::geography,
                             (SELECT location::geography FROM station WHERE id = :id), :radius)
            ORDER BY po.station_id, po.observed_at DESC
        ),
        me AS (SELECT price, observed_at FROM nearby WHERE station_id = :id)
        SELECT (SELECT price FROM me)        AS my_price,
               (SELECT observed_at FROM me)  AS my_observed_at,
               count(*)                                                   AS total,
               count(*) FILTER (WHERE n.price < (SELECT price FROM me))   AS cheaper,
               count(*) FILTER (WHERE n.price > (SELECT price FROM me))   AS dearer,
               min(n.price)                                               AS local_min,
               max(n.price)                                               AS local_max,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY n.price)       AS local_median
        FROM nearby n
        """;

    public Ranking ranking(long stationId, String fuel, boolean self, int radiusMeters, LocalDate since) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", stationId).addValue("fuel", fuel).addValue("self", self)
                .addValue("radius", radiusMeters).addValue("since", since);
        return jdbc.queryForObject(RANKING, p, (rs, i) -> {
            var myPrice = rs.getBigDecimal("my_price");
            int cheaper = rs.getInt("cheaper");
            int dearer = rs.getInt("dearer");
            int total = rs.getInt("total");
            return new Ranking(fuel, self, radiusMeters,
                    myPrice, rs.getObject("my_observed_at", LocalDate.class),
                    myPrice == null ? 0 : cheaper + 1, total, cheaper, dearer,
                    rs.getBigDecimal("local_min"), rs.getBigDecimal("local_median"), rs.getBigDecimal("local_max"));
        });
    }

    private static final String COMPETITOR_CHANGES = """
        WITH ch AS (
            SELECT po.station_id, po.observed_at, po.price,
                   lag(po.price) OVER (PARTITION BY po.station_id ORDER BY po.observed_at) AS prev
            FROM price_observation po
            WHERE lower(po.fuel_type) = lower(:fuel) AND po.is_self = :self
              AND po.observed_at >= :since
              AND po.station_id IN (
                    SELECT s.id FROM station s
                    WHERE s.id <> :id
                      AND ST_DWithin(s.location::geography,
                                     (SELECT location::geography FROM station WHERE id = :id), :radius))
        )
        SELECT ch.station_id, s.nome, s.bandiera, ch.observed_at, ch.prev, ch.price,
               (ch.price - ch.prev) AS delta
        FROM ch JOIN station s ON s.id = ch.station_id
        WHERE ch.prev IS NOT NULL AND ch.price <> ch.prev
        ORDER BY ch.observed_at DESC, abs(ch.price - ch.prev) DESC
        LIMIT :limit
        """;

    public List<CompetitorChange> competitorChanges(long stationId, String fuel, boolean self,
                                                     int radiusMeters, LocalDate since, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", stationId).addValue("fuel", fuel).addValue("self", self)
                .addValue("radius", radiusMeters).addValue("since", since).addValue("limit", limit);
        return jdbc.query(COMPETITOR_CHANGES, p, (rs, i) -> new CompetitorChange(
                rs.getLong("station_id"), rs.getString("nome"), rs.getString("bandiera"),
                rs.getObject("observed_at", LocalDate.class),
                rs.getBigDecimal("prev"), rs.getBigDecimal("price"), rs.getBigDecimal("delta")));
    }

    private static final String MOVERS = """
        WITH ch AS (
            SELECT po.station_id, po.observed_at, po.price,
                   lag(po.price) OVER (PARTITION BY po.station_id ORDER BY po.observed_at) AS prev
            FROM price_observation po
            WHERE lower(po.fuel_type) = lower(:fuel) AND po.is_self = :self
              AND po.observed_at >= :since
              AND po.station_id IN (
                    SELECT s.id FROM station s
                    WHERE ST_DWithin(s.location::geography,
                                     (SELECT location::geography FROM station WHERE id = :id), :radius))
        )
        SELECT ch.station_id, s.nome, s.bandiera,
               count(*) FILTER (WHERE ch.prev IS NOT NULL AND ch.price <> ch.prev)            AS changes,
               avg(abs(ch.price - ch.prev)) FILTER (WHERE ch.prev IS NOT NULL AND ch.price <> ch.prev) AS avg_delta,
               max(ch.observed_at) FILTER (WHERE ch.prev IS NOT NULL AND ch.price <> ch.prev) AS last_change,
               (ch.station_id = :id) AS is_mine
        FROM ch JOIN station s ON s.id = ch.station_id
        GROUP BY ch.station_id, s.nome, s.bandiera
        HAVING count(*) FILTER (WHERE ch.prev IS NOT NULL AND ch.price <> ch.prev) > 0
        ORDER BY changes DESC, avg_delta DESC
        LIMIT :limit
        """;

    public List<Mover> movers(long stationId, String fuel, boolean self,
                              int radiusMeters, LocalDate since, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", stationId).addValue("fuel", fuel).addValue("self", self)
                .addValue("radius", radiusMeters).addValue("since", since).addValue("limit", limit);
        return jdbc.query(MOVERS, p, (rs, i) -> new Mover(
                rs.getLong("station_id"), rs.getString("nome"), rs.getString("bandiera"),
                rs.getInt("changes"), rs.getBigDecimal("avg_delta"),
                rs.getObject("last_change", LocalDate.class), rs.getBoolean("is_mine")));
    }
}
