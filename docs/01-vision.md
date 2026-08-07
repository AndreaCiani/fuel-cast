# 01 — Vision

## The product

Two applications on one shared data layer, sourced from MIMIT's daily open-data
CSV of Italian fuel-station prices.

**Side A — consumer app (phase 1).** Drivers find cheap fuel near them or along a
route. The differentiator over existing tools is **historical data**: competitors
show only today's price. Core features:

- map + search by radius and along a route;
- historical percentile per station ("pricier than 82% of the last 90 days");
- net-saving calculator (detour cost vs. price difference per litre);
- "fill up now or wait?" signal from the local price trend.

**Side B — station-manager dashboard (phase 2).** Small station owners see how
they are positioned against local competitors:

- ranking within a configurable radius;
- alerts when a nearby competitor changes price;
- who moves price first in the area.

## Why it works as a project

- A **real, permissive** data source (IODL 2.0) with a **historical archive**
  back to 2015 — so the differentiating features work from day one.
- Genuine backend depth: ETL, geospatial queries, time-series.
- A two-sided product on one data model — Side B is new queries, not a rewrite.

## The moat is the archive

History is the whole advantage, so it must never be lost. Two safeguards:

1. every raw CSV is archived (gzip) **before** parsing, so a code bug can be
   reprocessed away;
2. ingestion is **idempotent** and **self-healing** — re-running a day is a
   no-op, and gaps are recoverable from the MIMIT archive.
