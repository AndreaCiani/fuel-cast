/** A station near a point, with its current price for the requested fuel. */
export interface NearbyStation {
  id: number;
  nome: string;
  bandiera: string;
  indirizzo: string;
  comune: string;
  provincia: string;
  latitude: number;
  longitude: number;
  distanceMeters: number;
  fuelType: string;
  self: boolean;
  price: number;
  observedAt: string;
}

/** Historical stats for one fuel/mode over a window. */
export interface FuelStat {
  fuelType: string;
  self: boolean;
  latestPrice: number;
  latestDate: string;
  observations: number;
  min: number;
  max: number;
  median: number;
  avg: number;
  /** Latest price is pricier than this % of the window (0 = cheapest, 100 = dearest). */
  pricierThanPct: number;
}

export interface StationDetail {
  id: number;
  nome: string;
  bandiera: string;
  tipoImpianto: string;
  indirizzo: string;
  comune: string;
  provincia: string;
  latitude: number | null;
  longitude: number | null;
  windowDays: number;
  fuels: FuelStat[];
}

export interface TrendPoint {
  date: string;
  avgPrice: number;
  stations: number;
}

export type TrendDirection = 'RISING' | 'FALLING' | 'STABLE' | 'INSUFFICIENT';

export interface LocalTrend {
  fuelType: string;
  self: boolean;
  radiusMeters: number;
  days: number;
  direction: TrendDirection;
  recentAvg: number | null;
  previousAvg: number | null;
  deltaPerLitre: number | null;
  points: TrendPoint[];
}
