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
- **Speed:** `reWriteBatchedInserts=true` on the datasource turns JDBC batches
  into multi-row INSERTs, a large speed-up for the tens-of-millions-of-rows load.
