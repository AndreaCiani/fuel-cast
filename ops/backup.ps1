<#
.SYNOPSIS
  Nightly backup of the fuel-cast Postgres database (the project's moat).

.DESCRIPTION
  Dumps the DB from the running Docker container in custom format (-Fc, already
  compressed), copies it to the host, prunes old local copies, and optionally
  uploads off-site to Cloudflare R2 via rclone. Custom format restores with
  pg_restore (see docs/05-deployment.md).

.EXAMPLE
  # Local only, keep 14 days:
  powershell -File ops\backup.ps1

.EXAMPLE
  # Also push off-site to an rclone R2 remote:
  powershell -File ops\backup.ps1 -RcloneRemote "r2:fuelcast-backups"

.NOTES
  Schedule with Windows Task Scheduler (daily, e.g. 03:00). See docs/05-deployment.md.
  The raw-CSV archive volume is NOT backed up here: it is re-downloadable from MIMIT.
#>
param(
  [string]$Container = "fc-db",
  [string]$Db = "fuelcast",
  [string]$User = "fuelcast",
  [string]$BackupDir = "C:\fuelcast-backups",
  [int]$KeepDays = 14,
  [string]$RcloneRemote = ""   # e.g. "r2:fuelcast-backups"; empty = local only
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupDir)) {
  New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$name  = "fuelcast-$stamp.dump"
$local = Join-Path $BackupDir $name

Write-Host "[backup] dumping $Db from container $Container ..."
# Dump inside the container to avoid PowerShell mangling the binary stream,
# then copy the file out. -Fc = custom (compressed) format.
docker exec $Container sh -c "pg_dump -U $User -Fc $Db > /tmp/$name"
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed (exit $LASTEXITCODE)" }

docker cp "${Container}:/tmp/$name" $local
docker exec $Container rm -f "/tmp/$name" | Out-Null

$sizeMB = [math]::Round((Get-Item $local).Length / 1MB, 1)
Write-Host "[backup] wrote $local ($sizeMB MB)"

# Prune local dumps older than KeepDays.
$cutoff = (Get-Date).AddDays(-$KeepDays)
Get-ChildItem -Path $BackupDir -Filter "fuelcast-*.dump" |
  Where-Object { $_.LastWriteTime -lt $cutoff } |
  ForEach-Object {
    Write-Host "[backup] pruning old $($_.Name)"
    Remove-Item $_.FullName -Force
  }

# Off-site copy (optional). rclone must be installed and the remote configured.
if ($RcloneRemote -ne "") {
  Write-Host "[backup] uploading to $RcloneRemote ..."
  & rclone copy $local $RcloneRemote
  if ($LASTEXITCODE -ne 0) { throw "rclone upload failed (exit $LASTEXITCODE)" }
  Write-Host "[backup] off-site upload OK"
} else {
  Write-Host "[backup] RcloneRemote not set - local backup only (set one for off-site safety)"
}

Write-Host "[backup] done."
