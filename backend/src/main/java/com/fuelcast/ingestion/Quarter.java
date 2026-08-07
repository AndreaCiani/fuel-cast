package com.fuelcast.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * A calendar quarter, matching the MIMIT archive naming ({@code YYYY_Q_tr}).
 * Parsed from {@code "YYYY-Q"} (e.g. {@code "2024-3"}).
 */
public record Quarter(int year, int q) implements Comparable<Quarter> {

    public Quarter {
        if (q < 1 || q > 4) throw new IllegalArgumentException("Quarter must be 1..4, got " + q);
        if (year < 2015) throw new IllegalArgumentException("MIMIT archive starts at 2015, got " + year);
    }

    public static Quarter parse(String s) {
        String[] parts = s.trim().split("-");
        if (parts.length != 2) throw new IllegalArgumentException("Expected 'YYYY-Q', got '" + s + "'");
        return new Quarter(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }

    public Quarter next() {
        return q == 4 ? new Quarter(year + 1, 1) : new Quarter(year, q + 1);
    }

    @Override
    public int compareTo(Quarter o) {
        return year != o.year ? Integer.compare(year, o.year) : Integer.compare(q, o.q);
    }

    /** Inclusive range of quarters from {@code from} to {@code to}. */
    public static List<Quarter> range(Quarter from, Quarter to) {
        if (from.compareTo(to) > 0) throw new IllegalArgumentException("from " + from + " is after to " + to);
        List<Quarter> out = new ArrayList<>();
        Quarter cur = from;
        while (cur.compareTo(to) <= 0) {
            out.add(cur);
            cur = cur.next();
        }
        return out;
    }

    @Override
    public String toString() {
        return year + "-" + q;
    }
}
