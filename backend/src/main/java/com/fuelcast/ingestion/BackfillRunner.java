package com.fuelcast.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the historical backfill at startup when {@code app.backfill.enabled=true}.
 * A one-off, explicitly-triggered batch job — never a public endpoint. Example:
 * {@code java -jar app.jar --app.backfill.enabled=true --app.backfill.from=2024-3 --app.backfill.to=2026-2}
 */
@Component
public class BackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunner.class);

    private final BackfillProperties props;
    private final BackfillService service;
    private final ConfigurableApplicationContext context;

    public BackfillRunner(BackfillProperties props, BackfillService service, ConfigurableApplicationContext context) {
        this.props = props;
        this.service = service;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!props.isEnabled()) return;
        if (props.getFrom() == null || props.getTo() == null) {
            throw new IllegalStateException("app.backfill.enabled=true requires app.backfill.from and app.backfill.to (e.g. 2024-3)");
        }
        Quarter from = Quarter.parse(props.getFrom());
        Quarter to = Quarter.parse(props.getTo());

        BackfillService.BackfillSummary summary = service.backfill(from, to);
        log.info("Backfill summary: {}", summary);

        if (props.isExitWhenDone()) {
            log.info("app.backfill.exit-when-done=true → shutting down");
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }
}
