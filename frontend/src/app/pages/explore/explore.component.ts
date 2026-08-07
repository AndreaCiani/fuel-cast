import { AfterViewInit, Component, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { FuelMapComponent } from '../../components/fuel-map/fuel-map.component';
import { StationDetailComponent } from '../../components/station-detail/station-detail.component';
import { StationService } from '../../services/station.service';
import { NearbyStation } from '../../models/station.model';

const DEFAULT_FUELS = ['Benzina', 'Gasolio', 'GPL', 'Metano'];
const ROME = { lat: 41.9028, lon: 12.4964 };

@Component({
  selector: 'app-explore',
  standalone: true,
  imports: [CommonModule, FormsModule, FuelMapComponent, StationDetailComponent],
  template: `
    <div class="relative h-[100dvh] w-full overflow-hidden font-sans">
      <app-fuel-map
        class="absolute inset-0 block"
        #map
        [stations]="stations()"
        [selectedId]="selectedId()"
        [initialCenter]="center"
        (markerSelect)="select($event)"
      />

      <section
        class="absolute z-10 flex flex-col gap-3
               left-2 right-2 bottom-2 max-h-[72dvh]
               md:inset-y-4 md:left-4 md:right-auto md:w-[23rem] md:max-h-none"
      >
        <!-- Controls -->
        <div class="rounded-2xl bg-white/95 p-4 shadow-xl ring-1 ring-slate-200 backdrop-blur">
          <div class="mb-3 flex items-center gap-2">
            <span class="text-xl">⛽</span>
            <div>
              <h1 class="text-base font-bold leading-none text-slate-900">fuel-cast</h1>
              <p class="text-[11px] text-slate-500">prezzi carburanti con lo storico</p>
            </div>
          </div>

          <div class="flex gap-2">
            <select
              class="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              [(ngModel)]="fuel"
              (ngModelChange)="search()"
            >
              @for (f of fuelTypes(); track f) {
                <option [value]="f">{{ f }}</option>
              }
            </select>
            <label class="flex items-center gap-1 rounded-lg border border-slate-300 px-2 text-xs text-slate-600">
              <input type="checkbox" [(ngModel)]="self" (ngModelChange)="search()" class="accent-brand-600" />
              Self
            </label>
          </div>

          <div class="mt-3">
            <div class="mb-1 flex justify-between text-xs text-slate-500">
              <span>Raggio</span><span class="tabular-nums">{{ radiusKm() }} km</span>
            </div>
            <input
              type="range"
              min="1"
              max="25"
              [(ngModel)]="radiusKmModel"
              (change)="search()"
              class="w-full accent-brand-600"
            />
          </div>

          <button
            (click)="search()"
            class="mt-3 w-full rounded-lg bg-brand-600 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 active:scale-[0.99]"
          >
            Cerca in questa zona
          </button>
        </div>

        <!-- List / detail -->
        <div class="min-h-0 flex-1 overflow-y-auto rounded-2xl bg-white/95 shadow-xl ring-1 ring-slate-200 backdrop-blur">
          @if (selectedId(); as id) {
            <button
              (click)="clearSelection()"
              class="flex w-full items-center gap-1 border-b border-slate-100 px-4 py-2 text-sm text-brand-700 hover:bg-slate-50"
            >
              ← Torna ai risultati
            </button>
            <app-station-detail [stationId]="id" [windowDays]="windowDays" />
          } @else {
            <div class="border-b border-slate-100 px-4 py-2 text-xs text-slate-500">
              @if (loading()) {
                Cerco…
              } @else if (error()) {
                <span class="text-rose-600">Errore nel caricamento. Riprova.</span>
              } @else {
                {{ stations().length }} stazioni · {{ fuel }} ({{ self() ? 'self' : 'servito' }})
              }
            </div>

            @for (s of stations(); track s.id) {
              <button
                (click)="select(s.id)"
                class="flex w-full items-center justify-between gap-3 border-b border-slate-50 px-4 py-3 text-left hover:bg-brand-50"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium text-slate-800">{{ s.nome || s.bandiera }}</p>
                  <p class="truncate text-xs text-slate-500">{{ s.bandiera }} · {{ distance(s.distanceMeters) }}</p>
                </div>
                <span class="shrink-0 tabular-nums text-sm font-semibold text-slate-900">{{ s.price | number: '1.3-3' }} €</span>
              </button>
            } @empty {
              @if (!loading()) {
                <p class="px-4 py-6 text-center text-sm text-slate-500">
                  Nessuna stazione trovata qui. Allarga il raggio o sposta la mappa.
                </p>
              }
            }

            <p class="px-4 py-2 text-[10px] leading-tight text-slate-400">
              Dati MIMIT (IODL 2.0), prezzi “alle 8” del giorno precedente. Mappa © OpenFreeMap / OpenStreetMap.
            </p>
          }
        </div>
      </section>
    </div>
  `,
})
export class ExploreComponent implements AfterViewInit {
  private readonly stationSvc = inject(StationService);
  private readonly map = viewChild.required(FuelMapComponent);

  readonly center = ROME;
  readonly windowDays = 90;

  readonly fuelTypes = signal<string[]>(DEFAULT_FUELS);
  fuel = 'Benzina';
  readonly self = signal(true);
  readonly radiusKm = signal(5);

  readonly stations = signal<NearbyStation[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);

  // ngModel bridges for signals
  get radiusKmModel(): number {
    return this.radiusKm();
  }
  set radiusKmModel(v: number) {
    this.radiusKm.set(Number(v));
  }

  ngAfterViewInit(): void {
    this.stationSvc.fuelTypes().subscribe({
      next: (types) => {
        if (types?.length) this.fuelTypes.set(types);
      },
      error: () => {},
    });

    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          this.map().flyTo(pos.coords.latitude, pos.coords.longitude);
          setTimeout(() => this.search(), 400);
        },
        () => this.search(),
        { timeout: 5000 },
      );
    } else {
      this.search();
    }
  }

  search(): void {
    const c = this.map().getCenter();
    this.loading.set(true);
    this.error.set(false);
    this.selectedId.set(null);
    this.stationSvc
      .nearby({ lat: c.lat, lon: c.lon, fuelType: this.fuel, self: this.self(), radiusMeters: this.radiusKm() * 1000, limit: 100 })
      .subscribe({
        next: (list) => {
          this.stations.set(list);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  select(id: number): void {
    this.selectedId.set(id);
    const s = this.stations().find((x) => x.id === id);
    if (s) this.map().flyTo(s.latitude, s.longitude);
  }

  clearSelection(): void {
    this.selectedId.set(null);
  }

  distance(meters: number): string {
    return meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`;
  }
}
