import { AfterViewInit, Component, computed, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { FuelMapComponent } from '../../components/fuel-map/fuel-map.component';
import { StationDetailComponent } from '../../components/station-detail/station-detail.component';
import { TrendSignalComponent } from '../../components/trend-signal/trend-signal.component';
import { StationService } from '../../services/station.service';
import { LocalTrend, NearbyStation } from '../../models/station.model';

const DEFAULT_FUELS = ['Benzina', 'Gasolio', 'GPL', 'Metano'];
const ROME = { lat: 41.9028, lon: 12.4964 };

@Component({
  selector: 'app-explore',
  standalone: true,
  imports: [CommonModule, FormsModule, FuelMapComponent, StationDetailComponent, TrendSignalComponent],
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
               left-2 right-2 bottom-2 max-h-[74dvh]
               md:inset-y-4 md:left-4 md:right-auto md:w-[24rem] md:max-h-none"
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
              <input type="checkbox" [(ngModel)]="selfModel" (ngModelChange)="search()" class="accent-brand-600" />
              Self
            </label>
          </div>

          <div class="mt-3">
            <div class="mb-1 flex justify-between text-xs text-slate-500">
              <span>Raggio</span><span class="tabular-nums">{{ radiusKm() }} km</span>
            </div>
            <input type="range" min="1" max="25" [(ngModel)]="radiusKmModel" (change)="search()" class="w-full accent-brand-600" />
          </div>

          <!-- Net-saving inputs -->
          <div class="mt-3 grid grid-cols-2 gap-2 text-xs text-slate-500">
            <label class="flex flex-col gap-1">
              Consumo (L/100km)
              <input type="number" min="1" max="30" step="0.1" [(ngModel)]="consumoModel"
                     class="rounded-lg border border-slate-300 px-2 py-1 text-sm text-slate-800" />
            </label>
            <label class="flex flex-col gap-1">
              Litri da fare
              <input type="number" min="1" max="120" step="1" [(ngModel)]="litriModel"
                     class="rounded-lg border border-slate-300 px-2 py-1 text-sm text-slate-800" />
            </label>
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
            @if (trend()) {
              <div class="p-3"><app-trend-signal [trend]="trend()" /></div>
            }

            <div class="flex items-center justify-between gap-2 border-y border-slate-100 px-4 py-2 text-xs text-slate-500">
              @if (loading()) {
                <span>Cerco…</span>
              } @else if (error()) {
                <span class="text-rose-600">Errore nel caricamento.</span>
              } @else {
                <span>{{ stations().length }} stazioni · {{ fuel }}</span>
              }
              <div class="flex overflow-hidden rounded-lg border border-slate-200 text-[11px]">
                <button (click)="sortMode.set('distanza')" [class]="sortBtn('distanza')">Distanza</button>
                <button (click)="sortMode.set('risparmio')" [class]="sortBtn('risparmio')">Risparmio</button>
              </div>
            </div>

            @for (s of sortedStations(); track s.id) {
              <button
                (click)="select(s.id)"
                class="flex w-full items-center justify-between gap-3 border-b border-slate-50 px-4 py-3 text-left hover:bg-brand-50"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm font-medium text-slate-800">{{ s.nome || s.bandiera }}</p>
                  <p class="truncate text-xs text-slate-500">
                    {{ s.bandiera }} · {{ distance(s.distanceMeters) }} · agg. {{ s.observedAt | date: 'dd/MM/yy' }}
                  </p>
                  @if (savingLabel(s); as lbl) {
                    <p class="mt-0.5 text-[11px] font-medium" [class]="savingClass(s)">{{ lbl }}</p>
                  }
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
              Risparmio netto stimato su andata+ritorno in linea d'aria.
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
  readonly consumo = signal(6.5);
  readonly litri = signal(40);

  readonly stations = signal<NearbyStation[]>([]);
  readonly trend = signal<LocalTrend | null>(null);
  readonly selectedId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);
  readonly sortMode = signal<'distanza' | 'risparmio'>('distanza');

  /** Nearest station (min distance) — baseline for net-saving. */
  private readonly nearest = computed(() => {
    const list = this.stations();
    return list.length ? list.reduce((a, b) => (a.distanceMeters <= b.distanceMeters ? a : b)) : null;
  });

  readonly sortedStations = computed(() => {
    const list = [...this.stations()];
    if (this.sortMode() === 'risparmio') {
      return list.sort((a, b) => (this.netSaving(b) ?? -1e9) - (this.netSaving(a) ?? -1e9));
    }
    return list.sort((a, b) => a.distanceMeters - b.distanceMeters);
  });

  // ngModel bridges for signals
  get selfModel() { return this.self(); }
  set selfModel(v: boolean) { this.self.set(v); }
  get radiusKmModel() { return this.radiusKm(); }
  set radiusKmModel(v: number) { this.radiusKm.set(Number(v)); }
  get consumoModel() { return this.consumo(); }
  set consumoModel(v: number) { this.consumo.set(Number(v)); }
  get litriModel() { return this.litri(); }
  set litriModel(v: number) { this.litri.set(Number(v)); }

  ngAfterViewInit(): void {
    this.stationSvc.fuelTypes().subscribe({
      next: (types) => { if (types?.length) this.fuelTypes.set(types); },
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
    const radiusMeters = this.radiusKm() * 1000;
    this.loading.set(true);
    this.error.set(false);
    this.selectedId.set(null);

    this.stationSvc
      .nearby({ lat: c.lat, lon: c.lon, fuelType: this.fuel, self: this.self(), radiusMeters, limit: 100 })
      .subscribe({
        next: (list) => { this.stations.set(list); this.loading.set(false); },
        error: () => { this.error.set(true); this.loading.set(false); },
      });

    this.trend.set(null);
    this.stationSvc
      .localTrend({ lat: c.lat, lon: c.lon, fuelType: this.fuel, self: this.self(), radiusMeters, days: 60 })
      .subscribe({ next: (t) => this.trend.set(t), error: () => {} });
  }

  select(id: number): void {
    this.selectedId.set(id);
    const s = this.stations().find((x) => x.id === id);
    if (s) this.map().flyTo(s.latitude, s.longitude);
  }

  clearSelection(): void {
    this.selectedId.set(null);
  }

  /** Net €saved by choosing s over the nearest station, minus the detour fuel cost. */
  netSaving(s: NearbyStation): number | null {
    const base = this.nearest();
    if (!base) return null;
    const litresSaving = (base.price - s.price) * this.litri();
    const extraKm = (2 * Math.max(0, s.distanceMeters - base.distanceMeters)) / 1000;
    const detourCost = extraKm * (this.consumo() / 100) * s.price;
    return litresSaving - detourCost;
  }

  savingLabel(s: NearbyStation): string | null {
    const base = this.nearest();
    if (!base) return null;
    if (s.id === base.id) return 'la più vicina';
    const net = this.netSaving(s);
    if (net === null) return null;
    const sign = net >= 0 ? '+' : '';
    return `risparmio netto ${sign}${net.toFixed(2)} €`;
  }

  savingClass(s: NearbyStation): string {
    const net = this.netSaving(s);
    if (net === null || this.nearest()?.id === s.id) return 'text-slate-400';
    if (net > 0.05) return 'text-brand-700';
    if (net < -0.05) return 'text-rose-600';
    return 'text-slate-400';
  }

  sortBtn(mode: 'distanza' | 'risparmio'): string {
    const active = this.sortMode() === mode;
    return active
      ? 'bg-brand-600 px-2 py-1 font-medium text-white'
      : 'bg-white px-2 py-1 text-slate-600 hover:bg-slate-50';
  }

  distance(meters: number): string {
    return meters >= 1000 ? `${(meters / 1000).toFixed(1)} km` : `${Math.round(meters)} m`;
  }
}
