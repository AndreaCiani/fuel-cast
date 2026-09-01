# 03 — Decision log

Records the choices made, the alternatives considered, and the reasoning — so we
remember *why* things are the way they are.

---

## D1 — Data source: MIMIT open data (IODL 2.0)

**Decision:** build on the MIMIT daily CSVs (`anagrafica_impianti_attivi.csv`,
`prezzo_alle_8.csv`).

**Why:** official, national coverage, updated daily, **IODL 2.0** licence (free
reuse incl. commercial, attribution required) — fits the "permissive licences
only" constraint. A **historical archive back to 2015** exists, so the
history-based features can be demonstrated immediately via backfill rather than
after months of collection.

**Obligation:** attribute MIMIT in the app and README. Prices are "as of 8am the
day before publication" (T-1), not real-time — state this to users.

---

## D2 — Stack: Java 21 + Spring Boot, Maven

**Decision:** same backend stack as the author's other project (`home-manager`).

**Why:** existing fluency; the technical depth for this project lives in the ETL,
geospatial and time-series work, not in learning a new language. Consistency
across the author's portfolio.

---

## D3 — Database: PostgreSQL + PostGIS

**Decision:** PostgreSQL with the **PostGIS** extension; geometry `Point(4326)`
on `station`, GIST-indexed.

**Why:** radius/route queries need real spatial indexing. PostGIS is the standard,
permissively licensed, and the coordinates ship in the source data (no geocoding).

---

## D4 — Bulk ETL via JDBC, not JPA

**Decision:** writes go through raw JDBC batch (`StationWriter`, `PriceWriter`);
JPA is reserved for the read side.

**Why:** backfilling years of prices is tens of millions of rows — the ORM's
per-entity overhead would dominate. Idempotency is expressed directly in SQL
(`ON CONFLICT`), which is clearer and faster than a read-modify-write in Java.

---

## D5 — price_observation: plain table now, partition later

**Decision:** keep `price_observation` a single table for now; range-partition by
`observed_at` (monthly) later.

**Why:** partitioning adds machinery (partition creation, maintenance) that isn't
worth it at week one. The unique key already leads with the future partition key,
so the migration is clean. **Trigger to revisit:** when history-window queries on
the full table exceed acceptable latency. Mirrors the author's D15-style stance
(don't add unsafe/complex machinery before it earns its place).

---

## D6 — Tests: Testcontainers (PostGIS), not H2

**Decision:** integration tests run against a real `postgis/postgis` container via
Testcontainers.

**Why — deviation from `home-manager` (which uses in-memory H2):** H2 does not
implement PostGIS. Spatial SQL and geometry columns would either fail or behave
differently from production, making H2 tests worthless for the geo/ETL core. A
real PostGIS container is the only faithful test. Cost: tests require a Docker
daemon (available on GitHub Actions runners).

---

## D7 — `location` geometry not mapped in JPA

**Decision:** the `Station` entity maps `latitude`/`longitude` but **not** the
`location` geometry column; ETL writes it via `ST_MakePoint`, and spatial reads
will use native SQL.

**Why:** avoids pulling a Hibernate-Spatial dependency and its dialect config for
a column only touched by bulk SQL and native geo queries. `ddl-auto=validate`
ignores unmapped columns, so the schema stays authoritative.

---

## D8 — Idempotent, self-healing ingestion

**Decision:** ingestion runs on boot and on a daily cron; the price insert is
`ON CONFLICT DO NOTHING` on `(station_id, fuel_type, is_self, observed_at)`.

**Why:** the archive is the product's moat, so ingestion must tolerate reboots,
retries and outages without corrupting or duplicating data. Re-running a day is a
no-op; missing days are recoverable from the MIMIT historical archive.

---

## D9 — Deployment: self-hosting at home + Cloudflare Tunnel

**Decision (plan):** host on the author's always-on home PC (static IP), fronted
by **Cloudflare Tunnel**.

**Why:** hardware already available; the Tunnel opens **no inbound ports**, hides
the home IP, and provides TLS + WAF/rate-limiting on the free plan — the correct
mitigation for exposing a home machine. The database is never published; the
backend is reached only over the internal Docker network. Details in
`05-deployment.md` (written when the public deploy is wired up).

---

## D10 — Historical backfill from the quarterly archive

**Decision:** backfill history from the MIMIT quarterly `.tar.gz` archive
(one daily CSV per file), parameterised by a quarter range and triggered as a
one-off batch (`app.backfill.enabled=true`, never a public endpoint). Initial
scope: **the last ~2 years** (~65M price rows) — enough for the 90-day percentile
and seasonal signal without needing table partitioning (see D5).

**Key choices, and why:**
- **Observation date from the filename** (`prezzo_alle_8-YYYYMMDD.csv`), not the
  CSV header — authoritative, and the archive's internal directory layout varies
  across years.
- **Separator auto-detected** per file: the archive mixes `;` (older) and `|`
  (recent), matching the same parser used by the daily feed.
- **Filter to the current station registry.** MIMIT station ids are stable, so
  keeping only prices for currently-known stations yields exactly the history the
  consumer product needs and drops closed-station noise — avoiding a ~5 GB
  download of the historical anagrafica.
- **Idempotent + resumable.** Same `ON CONFLICT DO NOTHING` write path, so an
  interrupted backfill is simply re-run.
- **Speed:** measured ~80k rows/day-file, ~20s/file — the dominant cost is unique-
  index maintenance on the growing fact table, not batch round-trips.
  (`reWriteBatchedInserts` was tried and dropped: no measurable speed-up here, and
  it makes `executeBatch` return `SUCCESS_NO_INFO`, breaking exact insert counts.)
  The real lever for a much larger load would be range partitioning (D5), not
  driver batch rewriting.

---

## D11 — Frontend: Angular 19 + Tailwind + MapLibre (PWA)

**Decision:** an Angular 19 standalone PWA styled with pure Tailwind (no component
library), map via **MapLibre GL** (BSD), base tiles from **OpenFreeMap**.

**Why — consistency & constraints:**
- Same stack shape as the author's `home-manager` (Angular standalone + pure
  Tailwind + Nginx-served PWA), so the portfolio reads as one hand.
- **MapLibre GL** is the permissively-licensed (BSD) fork of Mapbox GL — the right
  open map renderer.
- **Tiles are the one place a key/cap could sneak in.** OpenFreeMap serves vector
  tiles with **no API key and no usage cap** (and is self-hostable later), which
  fits the "open-source, permissive, no paid/usage-capped services" rule. The
  style URL is a one-line swap if we ever self-host tiles.
- No auth in phase 1: the consumer app is public, so there is no login, guard or
  interceptor — unlike `home-manager`.

**UX shape:** full-bleed map with floating control + results panels (a bottom
sheet on mobile). The station detail shows the historical-percentile bar — the
product's differentiator — with honest "insufficient history" states.

---

## D12 — Net-saving calculator & "fill up now or wait?" signal

**Decision:** split the two features by where the logic belongs.

- **Net-saving is client-side.** It only needs the current price and distance
  (already in the `/nearby` payload) plus two user inputs (consumption L/100km,
  litres). Saving vs the nearest station = `(priceNearest − price)·litres`
  minus the detour fuel cost, where the detour is a straight-line **round trip**
  beyond the nearest station (`2·Δdistance · consumption/100 · price`). Straight-
  line is an honest approximation, stated in the UI; routing (OSRM) would refine
  it later. Results can be sorted by distance or by net saving.

- **The wait/fill signal is backend/time-series.** `GET /api/trend/local`
  averages daily prices across stations within the radius over a window, and a
  `TrendService` compares the recent 7-day mean against the preceding baseline:
  RISING (fill now), FALLING (wait), STABLE, or INSUFFICIENT (hidden). This is
  where the historical archive pays off; the frontend draws it as a sparkline.

**Why the split:** keep the server doing the data-heavy aggregation and the
client doing cheap per-view arithmetic that reacts instantly to the consumption
inputs without a round trip.

---

## D13 — Side B (station-manager dashboard) on the same data layer

**Decision:** build Side B as new tables (`manager`, `manager_station`) that only
reference `station` and read `price_observation` — no change to Side A, no schema
rewrite. This was the promise of the phase-1 data model, now cashed in.

**Auth:** session cookie + Spring Security + BCrypt + cookie CSRF (mirrors
`home-manager`, D12-style). Only `/api/manager/**` and `/api/auth/me` require a
session; **Side A stays fully public** so the consumer demo needs no login.

**Station claiming:** a manager self-selects stations from the MIMIT registry —
no ownership verification (this is a public portfolio, not a real onboarding). A
**demo manager** is seeded (claiming a real Rome station) so recruiters can open
the dashboard with one click.

**The three features are queries over the existing history, not new pipelines:**
- **ranking** — DISTINCT-ON latest price per station within a radius, then locate
  the manager's price (rank, cheaper/dearer counts, local min/median/max);
- **competitor change feed** (the in-app "alerts") — a `LAG` window over
  `price_observation` surfacing where a neighbour's price actually moved;
- **price movers** — per-station change counts (a proxy for price leadership).
  Chosen over a stricter causal "who-moves-first" metric to stay honest and
  shippable; the stricter version is a future refinement.

**Alerts are in-app, not email** — a dashboard feed, no SMTP/external service
(keeps the "no paid/usage-capped services" constraint).
