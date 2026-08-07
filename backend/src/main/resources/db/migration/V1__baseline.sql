-- fuel-cast baseline schema.
-- Owned by Flyway; JPA runs with ddl-auto=validate and never changes it.

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- station: registry of active fuel stations (MIMIT anagrafica_impianti_attivi).
-- One row per station id; upserted on every ingest so the registry stays fresh.
-- ---------------------------------------------------------------------------
CREATE TABLE station (
    id            BIGINT PRIMARY KEY,              -- idImpianto from MIMIT
    gestore       TEXT,
    bandiera      TEXT,
    tipo_impianto TEXT,
    nome          TEXT,
    indirizzo     TEXT,
    comune        TEXT,
    provincia     TEXT,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    location      geometry(Point, 4326),           -- built from lon/lat during ETL
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_station_location  ON station USING GIST (location);
CREATE INDEX idx_station_provincia ON station (provincia);

-- ---------------------------------------------------------------------------
-- price_observation: the time-series fact table. One row per
-- (station, fuel type, self/served, snapshot date). This is the history that
-- is the whole point of the product.
--
-- NOTE on partitioning: this is intentionally a plain table for now. Range
-- partitioning by observed_at (monthly) is the planned optimisation once row
-- counts hurt query latency — see docs/03-decisions.md (D5). The unique key
-- already includes observed_at, so the future partition key is a clean change.
-- ---------------------------------------------------------------------------
CREATE TABLE price_observation (
    id              BIGSERIAL   PRIMARY KEY,
    station_id      BIGINT      NOT NULL REFERENCES station(id),
    fuel_type       TEXT        NOT NULL,          -- descCarburante (raw text)
    is_self         BOOLEAN     NOT NULL,          -- true = self, false = servito
    price           NUMERIC(6,3) NOT NULL,
    observed_at     DATE        NOT NULL,          -- extraction date of the snapshot
    communicated_at TIMESTAMPTZ,                   -- dtComu: when the operator set it
    ingested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_observation UNIQUE (station_id, fuel_type, is_self, observed_at)
);

CREATE INDEX idx_price_station_fuel_date ON price_observation (station_id, fuel_type, observed_at);
CREATE INDEX idx_price_observed_at       ON price_observation (observed_at);

-- ---------------------------------------------------------------------------
-- ingest_run: audit log of every ingestion, powering the /api/status health
-- view and making failures/gaps visible.
-- ---------------------------------------------------------------------------
CREATE TABLE ingest_run (
    id                BIGSERIAL PRIMARY KEY,
    snapshot_date     DATE,
    source            TEXT        NOT NULL,        -- DAILY | ARCHIVE | TEST
    status            TEXT        NOT NULL,        -- RUNNING | SUCCESS | FAILED
    stations_upserted INTEGER,
    prices_inserted   INTEGER,
    message           TEXT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ
);

CREATE INDEX idx_ingest_run_started_at ON ingest_run (started_at DESC);
