package com.fuelcast.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.GZIPOutputStream;

/**
 * Archives the raw CSV bytes (gzip) BEFORE parsing, laid out as
 * {@code <archiveDir>/yyyy/MM/<name>_yyyy-MM-dd.csv.gz}. This is the insurance
 * policy: if the parser has a bug or the MIMIT schema shifts, the untouched
 * source can be reprocessed — no day of history is ever lost to a code error.
 */
@Service
public class RawArchiveService {

    private static final Logger log = LoggerFactory.getLogger(RawArchiveService.class);

    private final IngestionProperties props;

    public RawArchiveService(IngestionProperties props) {
        this.props = props;
    }

    public Path archive(String name, String content, LocalDate date) {
        try {
            Path dir = Path.of(props.getArchiveDir())
                    .resolve(String.format("%04d", date.getYear()))
                    .resolve(String.format("%02d", date.getMonthValue()));
            Files.createDirectories(dir);
            Path target = dir.resolve("%s_%s.csv.gz".formatted(name, date));
            try (OutputStream fos = Files.newOutputStream(target);
                 GZIPOutputStream gz = new GZIPOutputStream(fos)) {
                gz.write(content.getBytes(StandardCharsets.UTF_8));
            }
            log.info("Archived raw {} -> {}", name, target);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to archive raw " + name + " for " + date, e);
        }
    }
}
