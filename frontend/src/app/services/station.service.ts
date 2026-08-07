import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { NearbyStation, StationDetail } from '../models/station.model';

/** Calls to the fuel-cast read API (/api/stations, /api/fuel-types). */
@Injectable({ providedIn: 'root' })
export class StationService {
  private readonly http = inject(HttpClient);

  nearby(opts: {
    lat: number;
    lon: number;
    fuelType: string;
    self?: boolean;
    radiusMeters?: number;
    limit?: number;
  }): Observable<NearbyStation[]> {
    let params = new HttpParams()
      .set('lat', opts.lat)
      .set('lon', opts.lon)
      .set('fuelType', opts.fuelType);
    if (opts.self !== undefined) params = params.set('self', opts.self);
    if (opts.radiusMeters !== undefined) params = params.set('radiusMeters', opts.radiusMeters);
    if (opts.limit !== undefined) params = params.set('limit', opts.limit);
    return this.http.get<NearbyStation[]>('/api/stations/nearby', { params });
  }

  detail(id: number, windowDays = 90): Observable<StationDetail> {
    const params = new HttpParams().set('windowDays', windowDays);
    return this.http.get<StationDetail>(`/api/stations/${id}`, { params });
  }

  fuelTypes(): Observable<string[]> {
    return this.http.get<string[]>('/api/fuel-types');
  }
}
