package com.fuelcast.station;

import com.fuelcast.station.dto.LocalTrend;
import com.fuelcast.station.dto.LocalTrend.TrendPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Turns a local daily-average price series into a direction signal for the
 * "fill up now or wait?" recommendation: compare the most recent days against
 * the preceding baseline.
 */
@Service
public class TrendService {

    /** Rising/falling only if the move exceeds half a cent per litre. */
    private static final BigDecimal THRESHOLD = new BigDecimal("0.005");
    private static final int RECENT_DAYS = 7;
    private static final int BASELINE_MAX_DAYS = 30;

    public LocalTrend build(String fuelType, boolean self, int radiusMeters, int days, List<TrendPoint> points) {
        if (points.size() < 4) {
            return new LocalTrend(fuelType, self, radiusMeters, days, "INSUFFICIENT", null, null, null, points);
        }

        int recentN = Math.min(RECENT_DAYS, points.size() / 2);
        List<TrendPoint> recent = points.subList(points.size() - recentN, points.size());
        List<TrendPoint> before = points.subList(0, points.size() - recentN);
        List<TrendPoint> baseline = before.size() > BASELINE_MAX_DAYS
                ? before.subList(before.size() - BASELINE_MAX_DAYS, before.size())
                : before;

        BigDecimal recentAvg = mean(recent);
        BigDecimal previousAvg = mean(baseline);
        BigDecimal delta = recentAvg.subtract(previousAvg).setScale(3, RoundingMode.HALF_UP);

        String direction;
        if (delta.compareTo(THRESHOLD) > 0) direction = "RISING";
        else if (delta.compareTo(THRESHOLD.negate()) < 0) direction = "FALLING";
        else direction = "STABLE";

        return new LocalTrend(fuelType, self, radiusMeters, days, direction, recentAvg, previousAvg, delta, points);
    }

    private static BigDecimal mean(List<TrendPoint> pts) {
        BigDecimal sum = BigDecimal.ZERO;
        for (TrendPoint p : pts) sum = sum.add(p.avgPrice());
        return sum.divide(BigDecimal.valueOf(pts.size()), 3, RoundingMode.HALF_UP);
    }
}
