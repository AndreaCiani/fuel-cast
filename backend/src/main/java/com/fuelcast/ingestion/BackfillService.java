package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.station.repository.StationRepository;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * One-off historical backfill from the MIMIT quarterly archive
 * ({@code .tar.gz} of daily {@code prezzo_alle_8-YYYYMMDD.csv} files).
 *
 * <p>Design notes:
 * <ul>
 *   <li>The observation date comes from the <b>filename</b>, not the CSV header
 *       (authoritative, and the internal directory layout varies across years).</li>
 *   <li>Prices are inserted filtered to the <b>current</b> station registry: MIMIT
 *       station ids are stable, so this keeps exactly the history the product needs
 *       (currently-open stations) and drops closed-station noise — no need to fetch
 *       the huge historical anagrafica.</li>
 *   <li>Inserts are idempotent ({@code ON CONFLICT DO NOTHING}), so a backfill that
 *       is interrupted can simply be re-run and resumes where it left off.</li>
 * </ul>
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);
    private static final Pattern FILE_DATE =
            Pattern.compile("prezzo_alle_8-(\\d{4})(\\d{2})(\\d{2})\\.csv$");

    private final BackfillProperties props;
    private final IngestionProperties ingestionProps;
    private final MimitDownloader downloader;
    private final MimitCsvParser parser;
    private final StationWriter stationWriter;
    private final PriceWriter priceWriter;
    private final StationRepository stationRepository;
    private final IngestRunRepository ingestRunRepository;

    public BackfillService(BackfillProperties props, IngestionProperties ingestionProps,
                           MimitDownloader downloader, MimitCsvParser parser, StationWriter stationWriter,
                           PriceWriter priceWriter, StationRepository stationRepository,
                           IngestRunRepository ingestRunRepository) {
        this.props = props;
        this.ingestionProps = ingestionProps;
        this.downloader = downloader;
        this.parser = parser;
        this.stationWriter = stationWriter;
        this.priceWriter = priceWriter;
        this.stationRepository = stationRepository;
        this.ingestRunRepository = ingestRunRepository;
    }

    public record BackfillSummary(int quarters, int days, long pricesInserted) { }

    /** Downloads and ingests every quarter in [from, to]. */
    public BackfillSummary backfill(Quarter from, Quarter to) throws Exception {
        List<Quarter> quarters = Quarter.range(from, to);
        log.info("Backfill {} -> {} ({} quarters)", from, to, quarters.size());

        ensureCurrentStations();
        Set<Long> knownIds = new HashSet<>(stationRepository.findAllIds());
        log.info("Backfill will keep prices for {} currently-known stations", knownIds.size());

        int totalDays = 0;
        long totalPrices = 0;
        for (Quarter q : quarters) {
            QuarterResult r = backfillQuarter(q, knownIds);
            totalDays += r.days();
            totalPrices += r.prices();
        }
        log.info("Backfill complete: {} quarters, {} days, {} prices inserted",
                quarters.size(), totalDays, totalPrices);
        return new BackfillSummary(quarters.size(), totalDays, totalPrices);
    }

    record QuarterResult(int days, long prices) { }

    private QuarterResult backfillQuarter(Quarter q, Set<Long> knownIds) throws Exception {
        String url = props.getPriceArchiveUrlTemplate().formatted(q.year(), q.year(), q.q());
        Path temp = Files.createTempFile("fuelcast-backfill-" + q + "-", ".tar.gz");
        IngestRun run = ingestRunRepository.save(new IngestRun("ARCHIVE", IngestRun.Status.RUNNING));
        try {
            log.info("[{}] downloading {}", q, url);
            downloader.downloadToFile(url, temp);
            QuarterResult r;
            try (InputStream in = Files.newInputStream(temp)) {
                r = processQuarterStream(q, in, knownIds);
            }
            run.setStatus(IngestRun.Status.SUCCESS);
            run.setPricesInserted((int) Math.min(r.prices(), Integer.MAX_VALUE));
            run.setMessage("%s: %d days, %d prices".formatted(q, r.days(), r.prices()));
            run.setFinishedAt(Instant.now());
            ingestRunRepository.save(run);
            return r;
        } catch (Exception e) {
            run.setStatus(IngestRun.Status.FAILED);
            run.setMessage("%s: %s".formatted(q, e.getMessage()));
            run.setFinishedAt(Instant.now());
            ingestRunRepository.save(run);
            throw e;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Processes a quarterly {@code .tar.gz} stream: one transaction-free bulk insert
     * per daily file. Package-visible so tests can drive it without downloading.
     */
    QuarterResult processQuarterStream(Quarter q, InputStream targzStream, Set<Long> knownIds) throws IOException {
        int days = 0;
        long prices = 0;
        try (GZIPInputStream gz = new GZIPInputStream(new BufferedInputStream(targzStream));
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Matcher m = FILE_DATE.matcher(entry.getName());
                if (!m.find()) continue;
                LocalDate date = LocalDate.of(
                        Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                String content = new String(tar.readAllBytes(), StandardCharsets.UTF_8);
                List<PriceRow> rows = parser.parsePrices(content, date);
                int inserted = priceWriter.insertAll(rows, knownIds);
                prices += inserted;
                days++;
                if (days % 15 == 0) log.info("[{}] {} days processed, {} prices so far", q, days, prices);
            }
        }
        log.info("[{}] done: {} days, {} prices", q, days, prices);
        return new QuarterResult(days, prices);
    }

    /** Refreshes the current station registry so historical prices have ids to join to. */
    private void ensureCurrentStations() throws Exception {
        log.info("Refreshing current station registry before backfill");
        String anagrafica = downloader.download(ingestionProps.getAnagraficaUrl());
        List<StationRow> stations = parser.parseStations(anagrafica);
        int n = stationWriter.upsertAll(stations);
        log.info("Registry upserted: {} stations", n);
    }
}
