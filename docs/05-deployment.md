# 05 — Deployment

fuel-cast self-hosts on an always-on **home PC** (Windows + Docker), exposed to
the internet through a **Cloudflare Tunnel**. No inbound router ports are opened
and the home IP is never exposed. (See D9 in the [decision log](03-decisions.md).)

```
Internet ── Cloudflare (TLS, WAF, rate-limit) ── Tunnel (outbound) ──▶ frontend:80 (Nginx)
                                                                         ├── serves the Angular PWA
                                                                         └── /api → backend:8080
                                                                                     └── db:5432 (PostGIS, private)
```

## Prerequisites

- Docker Desktop on the host.
- A domain managed in Cloudflare (free plan is enough).
- No static IP or port-forwarding required — the tunnel is outbound-only.

## 1. Configure `.env`

```bash
cp .env.example .env
```

Set a real `POSTGRES_PASSWORD` (and the matching `SPRING_DATASOURCE_PASSWORD`),
and leave `CLOUDFLARE_TUNNEL_TOKEN` empty for now.

## 2. Create the Cloudflare Tunnel

1. Cloudflare **Zero Trust dashboard → Networks → Tunnels → Create a tunnel**
   (type: *Cloudflared*). Name it e.g. `fuel-cast`.
2. Copy the **tunnel token** and put it in `.env` as `CLOUDFLARE_TUNNEL_TOKEN`.
3. Under the tunnel’s **Public Hostname**, add a route:
   - Subdomain/domain: e.g. `fuelcast.yourdomain.it`
   - Service: **HTTP** → `frontend:80`
   (The `cloudflared` container resolves `frontend` on the internal Docker network.)

Optionally add a **WAF rate-limiting rule** in the Cloudflare dashboard for the
hostname — the demo is public and unauthenticated (defense in depth on top of the
app-side radius/limit caps).

## 3. Run

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The prod overlay publishes **no host ports**: DB and backend are reachable only
inside the Docker network; the tunnel serves the app. Check health:

```bash
docker exec fc-backend curl -fsS http://localhost:8080/actuator/health
```

Then browse to your Cloudflare hostname.

> **Dev vs prod port clash:** only the dev file (`docker-compose.yml`) publishes
> `5432`, which would collide with a local Postgres. The prod overlay publishes
> nothing, so there is no clash on the home machine. For dev alongside a local
> Postgres, remap the host port (e.g. `55432:5432`).

## 4. Load data

The daily ingestion runs automatically (on startup + 09:30 Europe/Rome cron), so
the DB stays current. To load history once, run the backfill (see
[03-decisions.md](03-decisions.md) D10):

```bash
docker compose ... run --rm \
  -e BACKFILL_ENABLED=true -e BACKFILL_FROM=2024-3 -e BACKFILL_TO=2026-2 \
  -e BACKFILL_EXIT_WHEN_DONE=true backend
```

(Or set the `BACKFILL_*` vars in `.env` for a single run, then unset them.)

## 5. Backups — the moat

The price history is the product; it lives in the `pgdata` volume on one disk.
**Back it up off-site.** [`ops/backup.ps1`](../ops/backup.ps1) dumps the DB
(`pg_dump -Fc`), prunes old local copies, and optionally uploads to Cloudflare R2
via `rclone`.

Schedule it with **Windows Task Scheduler** (daily, e.g. 03:00):

- Action → Start a program:
  `powershell.exe -File C:\path\to\fuel-cast\ops\backup.ps1 -RcloneRemote "r2:fuelcast-backups"`

Set up the R2 remote once with `rclone config` (S3-compatible; endpoint from the
R2 dashboard). The raw-CSV archive volume (`rawdata`) is **not** backed up — it is
re-downloadable from MIMIT.

**Restore** (test it at least once):

```bash
docker cp fuelcast-YYYYMMDD-HHMMSS.dump fc-db:/tmp/restore.dump
docker exec fc-db pg_restore -U fuelcast -d fuelcast --clean --if-exists /tmp/restore.dump
```

## 6. Security checklist

- [ ] DB and backend ports **not** published (prod overlay) — verify `docker ps`.
- [ ] `.env` is git-ignored; real passwords set (not the defaults).
- [ ] Cloudflare rate-limiting rule on the public hostname.
- [ ] Automated off-site backups scheduled **and a restore tested**.
- [ ] Tunnel token treated as a secret.

## 7. Updating

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Flyway applies any new migrations on backend startup; the service worker serves
the new frontend build (index and `ngsw-worker.js` are sent `no-cache`).
