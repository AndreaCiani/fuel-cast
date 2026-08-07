package com.fuelcast.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** Audit record of one ingestion attempt. Powers the /api/status health view. */
@Entity
@Table(name = "ingest_run")
public class IngestRun {

    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String status;

    @Column(name = "stations_upserted")
    private Integer stationsUpserted;

    @Column(name = "prices_inserted")
    private Integer pricesInserted;

    private String message;

    @Column(name = "started_at", insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IngestRun() { }

    public IngestRun(String source, Status status) {
        this.source = source;
        this.status = status.name();
    }

    public Long getId() { return id; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

    public String getSource() { return source; }

    public String getStatus() { return status; }
    public void setStatus(Status status) { this.status = status.name(); }

    public Integer getStationsUpserted() { return stationsUpserted; }
    public void setStationsUpserted(Integer stationsUpserted) { this.stationsUpserted = stationsUpserted; }

    public Integer getPricesInserted() { return pricesInserted; }
    public void setPricesInserted(Integer pricesInserted) { this.pricesInserted = pricesInserted; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getStartedAt() { return startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
