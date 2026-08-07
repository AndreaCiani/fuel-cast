package com.fuelcast.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers ingestion on a daily cron and once at startup. Both paths are
 * wrapped so a failed pull (network, MIMIT down) is logged and recorded but
 * never crashes the app — the next run simply catches up, and because the write
 * path is idempotent, catching up is safe.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionService service;
    private final IngestionProperties props;

    public IngestionScheduler(IngestionService service, IngestionProperties props) {
        this.service = service;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isRunOnStartup()) {
            log.info("Startup ingestion disabled (app.ingestion.run-on-startup=false)");
            return;
        }
        log.info("Running startup ingestion");
        safelyIngest();
    }

    @Scheduled(cron = "${app.ingestion.daily-cron}", zone = "Europe/Rome")
    public void scheduled() {
        log.info("Running scheduled daily ingestion");
        safelyIngest();
    }

    private void safelyIngest() {
        try {
            service.ingestDaily();
        } catch (Exception e) {
            log.error("Ingestion run failed and was swallowed; will retry on next trigger", e);
        }
    }
}
