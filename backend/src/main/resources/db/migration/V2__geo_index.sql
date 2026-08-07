-- Functional GIST index on the geography cast, so radius queries
-- (ST_DWithin(location::geography, point, meters)) are index-assisted as the
-- station table grows. The station table stays small (~24k rows), but this is
-- the correct spatial index for metre-based proximity search.
CREATE INDEX IF NOT EXISTS idx_station_location_geog
    ON station USING GIST ((location::geography));
