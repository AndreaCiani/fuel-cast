package com.fuelcast.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the two MIMIT CSV files.
 *
 * <p>Both files share the same shape: line 1 is {@code "Estrazione del yyyy-MM-dd"},
 * line 2 is the header, then data rows. The field separator is a pipe ({@code |})
 * in the current feed (since 2026-02-10) but was historically {@code ;}/{@code ,};
 * it is auto-detected from the header so archive files parse too.
 */
@Component
public class MimitCsvParser {

    private static final Logger log = LoggerFactory.getLogger(MimitCsvParser.class);

    private static final Pattern EXTRACTION_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    /** Sanity cap: fuel is never ≥100 €/L, and NUMERIC(6,3) overflows at 1000. */
    private static final BigDecimal MAX_PRICE = new BigDecimal("100");
    private static final DateTimeFormatter DT_COMU = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** A parsed station registry row. */
    public record StationRow(
            long id, String gestore, String bandiera, String tipoImpianto, String nome,
            String indirizzo, String comune, String provincia, Double latitude, Double longitude) { }

    /** A parsed price row, tagged with the snapshot (extraction) date. */
    public record PriceRow(
            long stationId, String fuelType, boolean self, BigDecimal price,
            LocalDate observedAt, Instant communicatedAt) { }

    /** Reads the "Estrazione del yyyy-MM-dd" header line; falls back to {@code fallback}. */
    public LocalDate parseExtractionDate(String content, LocalDate fallback) {
        String firstLine = firstLine(content);
        Matcher m = EXTRACTION_DATE.matcher(firstLine);
        if (m.find()) {
            return LocalDate.parse(m.group(1));
        }
        log.warn("No extraction date in header line '{}', using fallback {}", firstLine, fallback);
        return fallback;
    }

    public List<StationRow> parseStations(String content) {
        List<StationRow> out = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 3) return out;
        String sep = detectSeparator(lines[1]);
        int malformed = 0;
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] f = line.split(Pattern.quote(sep), -1);
            if (f.length < 10) { malformed++; continue; }
            try {
                out.add(new StationRow(
                        Long.parseLong(f[0].trim()),
                        trimToNull(f[1]), trimToNull(f[2]), trimToNull(f[3]), trimToNull(f[4]),
                        trimToNull(f[5]), trimToNull(f[6]), trimToNull(f[7]),
                        parseCoord(f[8]), parseCoord(f[9])));
            } catch (RuntimeException e) {
                malformed++;
            }
        }
        if (malformed > 0) log.warn("Skipped {} malformed anagrafica rows", malformed);
        return out;
    }

    public List<PriceRow> parsePrices(String content, LocalDate observedAt) {
        List<PriceRow> out = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 3) return out;
        String sep = detectSeparator(lines[1]);
        int malformed = 0;
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] f = line.split(Pattern.quote(sep), -1);
            if (f.length < 5) { malformed++; continue; }
            try {
                BigDecimal price = new BigDecimal(f[2].trim().replace(',', '.'));
                // Drop out-of-range garbage: no fuel is ≤0 or ≥100 €/L, and the
                // price column is NUMERIC(6,3) — a stray ≥1000 would overflow and
                // fail the whole batch. Skip it as malformed instead.
                if (price.signum() <= 0 || price.compareTo(MAX_PRICE) >= 0) { malformed++; continue; }
                out.add(new PriceRow(
                        Long.parseLong(f[0].trim()),
                        trimToNull(f[1]),
                        "1".equals(f[3].trim()),
                        price,
                        observedAt,
                        parseCommunicatedAt(f[4])));
            } catch (RuntimeException e) {
                malformed++;
            }
        }
        if (malformed > 0) log.warn("Skipped {} malformed prezzo rows", malformed);
        return out;
    }

    // --- helpers ---

    private static String firstLine(String content) {
        int nl = content.indexOf('\n');
        return (nl < 0 ? content : content.substring(0, nl)).trim();
    }

    /** Pick the separator that appears most often in the header line. */
    static String detectSeparator(String header) {
        int pipes = count(header, '|');
        int semis = count(header, ';');
        int commas = count(header, ',');
        if (pipes >= semis && pipes >= commas) return "|";
        if (semis >= commas) return ";";
        return ",";
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Parses a coordinate; treats blanks, non-numbers and (0,0) as absent. */
    private static Double parseCoord(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            return v == 0.0 ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant parseCommunicatedAt(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return null;
        try {
            return LocalDateTime.parse(t, DT_COMU).atZone(ROME).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
