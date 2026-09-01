import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ClaimedStation, CompetitorChange, Mover, Ranking } from '../models/manager.model';

/** Calls to the station-manager dashboard API (/api/manager/**). */
@Injectable({ providedIn: 'root' })
export class ManagerService {
  private readonly http = inject(HttpClient);

  myStations(): Observable<ClaimedStation[]> {
    return this.http.get<ClaimedStation[]>('/api/manager/stations');
  }

  claim(stationId: number): Observable<ClaimedStation> {
    return this.http.post<ClaimedStation>('/api/manager/stations', { stationId });
  }

  unclaim(stationId: number): Observable<void> {
    return this.http.delete<void>(`/api/manager/stations/${stationId}`);
  }

  ranking(stationId: number, fuelType: string, self: boolean, radiusMeters: number): Observable<Ranking> {
    const params = new HttpParams().set('fuelType', fuelType).set('self', self).set('radiusMeters', radiusMeters);
    return this.http.get<Ranking>(`/api/manager/stations/${stationId}/ranking`, { params });
  }

  competitors(
    stationId: number,
    fuelType: string,
    self: boolean,
    radiusMeters: number,
    days = 90,
  ): Observable<CompetitorChange[]> {
    const params = new HttpParams()
      .set('fuelType', fuelType)
      .set('self', self)
      .set('radiusMeters', radiusMeters)
      .set('days', days);
    return this.http.get<CompetitorChange[]>(`/api/manager/stations/${stationId}/competitors`, { params });
  }

  movers(
    stationId: number,
    fuelType: string,
    self: boolean,
    radiusMeters: number,
    days = 90,
  ): Observable<Mover[]> {
    const params = new HttpParams()
      .set('fuelType', fuelType)
      .set('self', self)
      .set('radiusMeters', radiusMeters)
      .set('days', days);
    return this.http.get<Mover[]>(`/api/manager/stations/${stationId}/movers`, { params });
  }
}
