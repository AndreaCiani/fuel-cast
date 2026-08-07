package com.fuelcast.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the MIMIT ingestion pipeline (prefix {@code app.ingestion}). */
@ConfigurationProperties("app.ingestion")
public class IngestionProperties {

    private String anagraficaUrl;
    private String prezzoUrl;
    private String archiveDir = "./data/raw";
    private boolean runOnStartup = true;
    private String dailyCron = "0 30 9 * * *";
    private int downloadTimeoutSeconds = 180;

    public String getAnagraficaUrl() { return anagraficaUrl; }
    public void setAnagraficaUrl(String anagraficaUrl) { this.anagraficaUrl = anagraficaUrl; }

    public String getPrezzoUrl() { return prezzoUrl; }
    public void setPrezzoUrl(String prezzoUrl) { this.prezzoUrl = prezzoUrl; }

    public String getArchiveDir() { return archiveDir; }
    public void setArchiveDir(String archiveDir) { this.archiveDir = archiveDir; }

    public boolean isRunOnStartup() { return runOnStartup; }
    public void setRunOnStartup(boolean runOnStartup) { this.runOnStartup = runOnStartup; }

    public String getDailyCron() { return dailyCron; }
    public void setDailyCron(String dailyCron) { this.dailyCron = dailyCron; }

    public int getDownloadTimeoutSeconds() { return downloadTimeoutSeconds; }
    public void setDownloadTimeoutSeconds(int downloadTimeoutSeconds) { this.downloadTimeoutSeconds = downloadTimeoutSeconds; }
}
