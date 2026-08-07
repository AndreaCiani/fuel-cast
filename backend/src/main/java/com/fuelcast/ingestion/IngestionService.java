package com.fuelcast.ingestion;

import com.fuelcast.ingestion.MimitCsvParser.PriceRow;
import com.fuelcast.ingestion.MimitCsvParser.StationRow;
import com.fuelcast.station.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates one ingestion: download → archive raw → parse → bulk upsert.
 *
 * <p>Every run is recorded in {@code ingest_run}. The write path is idempotent
 * (see {@link PriceWriter}), so re-running a day is safe and re-running on every
 * boot keeps a fresh instance current without duplicating data.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionProperties props;
    private final MimitDownloader downloader;
    private final MimitCsvParser parser;
    private final RawArchiveService archive;
    private final StationWriter stationWriter;
    private final PriceWriter priceWriter;
    private final StationRepository stationRepository;
    private final IngestRunRepository ingestRunRepository;

    public IngestionService(IngestionProperties props, MimitDownloader downloader, MimitCsvParser parser,
                            RawArchiveService archive, StationWriter stationWriter, PriceWriter priceWriter,
                            StationRepository stationRepository, IngestRunRepository ingestRunRepository) {
        this.props = props;
        this.downloader = downloader;
        this.parser = parser;
        this.archive = archive;
        this.stationWriter = stationWriter;
        this.priceWriter = priceWriter;
        this.stationRepository = stationRepository;
        this.ingestRunRepository = ingestRunRepository;
    }

    /** Downloads today's MIMIT snapshot and ingests it. */
    public IngestRun ingestDaily() throws Exception {
        log.info("Starting daily MIMIT ingestion");
        String anagrafica = downloader.download(props.getAnagraficaUrl());
        String prezzo = downloader.download(props.getPrezzoUrl());
        return ingest(anagrafica, prezzo, "DAILY");
    }

    /**
     * Ingests already-fetched CSV content. Extracted from {@link #ingestDaily()}
     * so tests can drive the full pipeline without network access.
     */
    @Transactional
    public IngestRun ingest(String anagraficaCsv, String prezzoCsv, String source) {
        IngestRun run = ingestRunRepository.save(new IngestRun(source, IngestRun.Status.RUNNING));
        try {
            LocalDate snapshotDate = parser.parseExtractionDate(prezzoCsv, LocalDate.now());
            run.setSnapshotDate(snapshotDate);

            // Archive raw first — before any parsing can go wrong.
            archive.archive("anagrafica", anagraficaCsv, snapshotDate);
            archive.archive("prezzo", prezzoCsv, snapshotDate);

            List<StationRow> stations = parser.parseStations(anagraficaCsv);
            List<PriceRow> prices = parser.parsePrices(prezzoCsv, snapshotDate);

            int upserted = stationWriter.upsertAll(stations);
            Set<Long> knownIds = new HashSet<>(stationRepository.findAllIds());
            int inserted = priceWriter.insertAll(prices, knownIds);

            run.setStationsUpserted(upserted);
            run.setPricesInserted(inserted);
            run.setStatus(IngestRun.Status.SUCCESS);
            run.setFinishedAt(Instant.now());
            log.info("Ingestion {} OK: {} stations, {} new prices for {}",
                    source, upserted, inserted, snapshotDate);
            return ingestRunRepository.save(run);
        } catch (RuntimeException e) {
            run.setStatus(IngestRun.Status.FAILED);
            run.setMessage(truncate(e.getMessage()));
            run.setFinishedAt(Instant.now());
            ingestRunRepository.save(run);
            log.error("Ingestion {} FAILED", source, e);
            throw e;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
