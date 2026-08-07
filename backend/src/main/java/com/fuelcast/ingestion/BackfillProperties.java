package com.fuelcast.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the one-off historical backfill (prefix {@code app.backfill}).
 * Disabled by default; enabled explicitly for a run, e.g.
 * {@code --app.backfill.enabled=true --app.backfill.from=2024-3 --app.backfill.to=2026-2}.
 */
@ConfigurationProperties("app.backfill")
public class BackfillProperties {

    private boolean enabled = false;
    private String from;
    private String to;
    /** Stop the application once the backfill completes (useful for a batch run). */
    private boolean exitWhenDone = false;
    /** URL template: args are (year, year, quarter). */
    private String priceArchiveUrlTemplate =
            "https://opendatacarburanti.mise.gov.it/categorized/prezzo_alle_8/%d/%d_%d_tr.tar.gz";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public boolean isExitWhenDone() { return exitWhenDone; }
    public void setExitWhenDone(boolean exitWhenDone) { this.exitWhenDone = exitWhenDone; }

    public String getPriceArchiveUrlTemplate() { return priceArchiveUrlTemplate; }
    public void setPriceArchiveUrlTemplate(String priceArchiveUrlTemplate) { this.priceArchiveUrlTemplate = priceArchiveUrlTemplate; }
}
