package com.fuelcast.manager.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Link between a manager and a station they manage. */
@Entity
@Table(name = "manager_station")
public class ManagerStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    protected ManagerStation() { }

    public ManagerStation(Long managerId, Long stationId) {
        this.managerId = managerId;
        this.stationId = stationId;
    }

    public Long getId() { return id; }
    public Long getManagerId() { return managerId; }
    public Long getStationId() { return stationId; }
}
