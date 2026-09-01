-- Side B: station-manager accounts. Built on the same data layer — these tables
-- only reference station and read price_observation; nothing in Side A changes.

CREATE TABLE manager (
    id            BIGSERIAL   PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Which stations a manager manages. No ownership verification (public demo):
-- a manager self-selects stations from the MIMIT registry.
CREATE TABLE manager_station (
    id         BIGSERIAL   PRIMARY KEY,
    manager_id BIGINT      NOT NULL REFERENCES manager(id) ON DELETE CASCADE,
    station_id BIGINT      NOT NULL REFERENCES station(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_manager_station UNIQUE (manager_id, station_id)
);

CREATE INDEX idx_manager_station_manager ON manager_station (manager_id);
