import { Component, effect, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { StationService } from '../../services/station.service';
import { FuelStat, StationDetail } from '../../models/station.model';

@Component({
  selector: 'app-station-detail',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (loading()) {
      <div class="p-4 text-sm text-slate-500">Carico lo storico…</div>
    } @else if (error()) {
      <div class="p-4 text-sm text-rose-600">Impossibile caricare il dettaglio.</div>
    } @else if (detail()) {
      @let d = detail()!;
      <div class="space-y-4 p-4">
        <header>
          <h2 class="text-lg font-semibold leading-tight text-slate-900">{{ d.nome || d.bandiera }}</h2>
          <p class="text-sm text-slate-500">{{ d.bandiera }} · {{ d.indirizzo }}, {{ d.comune }} ({{ d.provincia }})</p>
        </header>

        @if (d.fuels.length === 0) {
          <p class="rounded-lg bg-slate-100 p-3 text-sm text-slate-600">
            Storico insufficiente per questa stazione nella finestra di {{ d.windowDays }} giorni.
          </p>
        } @else {
          <p class="text-xs uppercase tracking-wide text-slate-400">
            Posizione del prezzo attuale rispetto agli ultimi {{ d.windowDays }} giorni
          </p>
          <div class="space-y-3">
            @for (f of d.fuels; track f.fuelType + f.self) {
              <div class="rounded-xl border border-slate-200 p-3">
                <div class="mb-2 flex items-baseline justify-between">
                  <div class="flex items-center gap-2">
                    <span class="font-medium text-slate-800">{{ f.fuelType }}</span>
                    <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500">
                      {{ f.self ? 'Self' : 'Servito' }}
                    </span>
                  </div>
                  <span class="text-lg font-semibold tabular-nums text-slate-900">{{ f.latestPrice | number: '1.3-3' }} €</span>
                </div>

                <!-- Percentile track: min (left) → max (right), marker at current position -->
                <div class="relative h-2 rounded-full bg-gradient-to-r from-brand-500 via-amber-400 to-rose-500">
                  <div
                    class="absolute -top-1 h-4 w-1.5 -translate-x-1/2 rounded-full bg-slate-900 ring-2 ring-white"
                    [style.left.%]="f.pricierThanPct"
                  ></div>
                </div>
                <div class="mt-1 flex justify-between text-[11px] tabular-nums text-slate-400">
                  <span>{{ f.min | number: '1.3-3' }}</span>
                  <span>mediana {{ f.median | number: '1.3-3' }}</span>
                  <span>{{ f.max | number: '1.3-3' }}</span>
                </div>

                <p class="mt-2 text-sm font-medium" [class]="verdictClass(f)">{{ verdict(f) }}</p>
                <p class="text-[11px] text-slate-400">{{ f.observations }} rilevazioni</p>
              </div>
            }
          </div>
        }
      </div>
    }
  `,
})
export class StationDetailComponent {
  private readonly stations = inject(StationService);

  readonly stationId = input.required<number>();
  readonly windowDays = input<number>(90);

  readonly detail = signal<StationDetail | null>(null);
  readonly loading = signal(false);
  readonly error = signal(false);

  constructor() {
    effect(() => {
      const id = this.stationId();
      const days = this.windowDays();
      this.loading.set(true);
      this.error.set(false);
      this.detail.set(null);
      this.stations.detail(id, days).subscribe({
        next: (d) => {
          this.detail.set(d);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
    });
  }

  verdict(f: FuelStat): string {
    const p = f.pricierThanPct;
    if (p <= 25) return 'Ottimo momento — più economico del solito';
    if (p <= 50) return 'Sotto la media del periodo';
    if (p <= 75) return `Sopra la media — più caro del ${p}% dei giorni`;
    return `Caro — più alto del ${p}% degli ultimi giorni`;
  }

  verdictClass(f: FuelStat): string {
    const p = f.pricierThanPct;
    if (p <= 25) return 'text-brand-700';
    if (p <= 50) return 'text-brand-600';
    if (p <= 75) return 'text-amber-600';
    return 'text-rose-600';
  }
}
