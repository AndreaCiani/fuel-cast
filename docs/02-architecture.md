# 02 — Architecture

## Overview

```
        MIMIT open data (daily CSV, pipe-separated, IODL 2.0)
                 │  anagrafica_impianti_attivi.csv
                 │  prezzo_alle_8.csv
                 ▼
   ┌─────────────────────────────────────────────┐
   │  Ingestion (Spring Boot)                     │
   │  download → archive raw (gzip) → parse →     │
   │  bulk upsert (JDBC) → record ingest_run      │
   └─────────────────────────────────────────────┘
                 ▼
   ┌─────────────────────────────────────────────┐
   │  PostgreSQL + PostGIS                         │
   │  station · price_observation · ingest_run    │
   └─────────────────────────────────────────────┘
                 ▼
   ┌─────────────────────────────────────────────┐
   │  REST API (Spring Boot)                      │
   │  /api/status  (geo & history endpoints next) │
   └─────────────────────────────────────────────┘
                 ▼
        Angular PWA (Tailwind)  —  added in a later phase
                 ▼
        Nginx (static + /api proxy) → Cloudflare Tunnel
```

## Two write paths, on purpose

- **Bulk ETL** (`StationWriter`, `PriceWriter`) uses raw **JDBC batch**, not JPA.
  Backfilling years of history is tens of millions of rows; the ORM would be the
  bottleneck. Idempotency is enforced in SQL (`ON CONFLICT`).
- **Read/API** uses **JPA** for ordinary queries. Spatial reads (radius, route)
  will use native SQL against the PostGIS `location` column.

## Data model

- `station` — one row per MIMIT station id (the source owns identity). Holds
  `latitude`/`longitude` plus a PostGIS `location` geometry (GIST-indexed) built
  during ETL. Upserted every run; `last_seen_at` tracks liveness.
- `price_observation` — the time-series fact table. Unique on
  `(station_id, fuel_type, is_self, observed_at)`, which is what makes ingestion
  idempotent. `observed_at` is the snapshot's extraction date.
- `ingest_run` — audit of every run; backs `/api/status`.

**Side B needs no rewrite:** its future tables (`manager_account`,
`manager_station`, `alert_rule`) only reference `station` and read
`price_observation`. "Who moves price first" is a query over the same fact table.

## Ingestion lifecycle

1. On boot (`run-on-startup`) and on a daily cron (09:30 Europe/Rome).
2. Download both CSVs → archive raw gzip under `data/raw/yyyy/MM/`.
3. Parse (separator auto-detected; malformed rows counted and skipped).
4. Upsert stations, then insert prices for known stations (`ON CONFLICT DO NOTHING`).
5. Record success/failure with counts in `ingest_run`.

Failures are logged and recorded, never fatal — the next trigger catches up, and
idempotency makes catching up safe.

## Local run

`docker compose up --build` → PostGIS + backend. `GET /api/status` shows freshness.
Production overlays `docker-compose.prod.yml` (no published ports; Cloudflare
Tunnel fronts the app).
