export interface Manager {
  id: number;
  email: string;
  displayName: string;
}

export interface ClaimedStation {
  stationId: number;
  nome: string;
  bandiera: string;
  comune: string;
  provincia: string;
  latitude: number | null;
  longitude: number | null;
}

export interface Ranking {
  fuelType: string;
  self: boolean;
  radiusMeters: number;
  myPrice: number | null;
  myObservedAt: string | null;
  rank: number;
  total: number;
  cheaperThanMe: number;
  dearerThanMe: number;
  localMin: number | null;
  localMedian: number | null;
  localMax: number | null;
}

export interface CompetitorChange {
  stationId: number;
  nome: string;
  bandiera: string;
  date: string;
  previousPrice: number;
  newPrice: number;
  delta: number;
}

export interface Mover {
  stationId: number;
  nome: string;
  bandiera: string;
  changes: number;
  avgAbsDelta: number;
  lastChange: string;
  isMine: boolean;
}
