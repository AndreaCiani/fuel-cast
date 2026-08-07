package com.fuelcast.price.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single daily price point for a station/fuel/mode. The fact table row.
 *
 * <p>Rows are written in bulk via JDBC (see {@code PriceWriter}); this entity
 * exists for the read side (status view now, API queries later).
 */
@Entity
@Table(name = "price_observation")
public class PriceObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    @Column(name = "is_self", nullable = false)
    private boolean self;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "observed_at", nullable = false)
    private LocalDate observedAt;

    @Column(name = "communicated_at")
    private Instant communicatedAt;

    @Column(name = "ingested_at", insertable = false, updatable = false)
    private Instant ingestedAt;

    public Long getId() { return id; }
    public Long getStationId() { return stationId; }
    public String getFuelType() { return fuelType; }
    public boolean isSelf() { return self; }
    public BigDecimal getPrice() { return price; }
    public LocalDate getObservedAt() { return observedAt; }
    public Instant getCommunicatedAt() { return communicatedAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}
