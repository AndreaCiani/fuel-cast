import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { ManagerService } from '../../services/manager.service';
import { ClaimedStation, CompetitorChange, Mover, Ranking } from '../../models/manager.model';

const FUELS = ['Benzina', 'Gasolio', 'GPL', 'Metano'];

@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-[100dvh] bg-slate-100 font-sans">
      <!-- Top bar -->
      <header class="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
        <div class="flex items-center gap-2">
          <span class="text-xl">⛽</span>
          <div>
            <h1 class="text-sm font-bold leading-none text-slate-900">fuel-cast</h1>
            <p class="text-[11px] text-slate-500">dashboard gestore</p>
          </div>
        </div>
        <div class="flex items-center gap-3 text-sm">
          <span class="hidden text-slate-500 sm:inline">{{ auth.manager()?.displayName }}</span>
          <a routerLink="/" class="text-brand-700 hover:underline">Mappa</a>
          <button (click)="logout()" class="rounded-lg border border-slate-300 px-2 py-1 text-slate-600 hover:bg-slate-50">Esci</button>
        </div>
      </header>

      <main class="mx-auto max-w-5xl space-y-4 p-4">
        <!-- Station + controls -->
        <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          @if (stations().length) {
            <div class="flex flex-wrap items-end gap-3">
              <label class="flex flex-col gap-1 text-xs text-slate-500">
                Stazione
                <select [(ngModel)]="stationId" (ngModelChange)="load()"
                        class="min-w-[16rem] rounded-lg border border-slate-300 px-2 py-2 text-sm text-slate-800">
                  @for (s of stations(); track s.stationId) {
                    <option [value]="s.stationId">{{ s.nome || s.bandiera }} — {{ s.comune }}</option>
                  }
                </select>
              </label>
              <label class="flex flex-col gap-1 text-xs text-slate-500">
                Carburante
                <select [(ngModel)]="fuel" (ngModelChange)="load()" class="rounded-lg border border-slate-300 px-2 py-2 text-sm">
                  @for (f of fuels; track f) { <option [value]="f">{{ f }}</option> }
                </select>
              </label>
              <label class="flex items-center gap-1 rounded-lg border border-slate-300 px-2 py-2 text-xs text-slate-600">
                <input type="checkbox" [(ngModel)]="selfModel" (ngModelChange)="load()" class="accent-brand-600" /> Self
              </label>
              <label class="flex flex-col gap-1 text-xs text-slate-500">
                Raggio: {{ radiusKm() }} km
                <input type="range" min="1" max="25" [(ngModel)]="radiusModel" (change)="load()" class="accent-brand-600" />
              </label>
            </div>
          } @else {
            <p class="text-sm text-slate-600">
              Non gestisci ancora nessuna stazione. Rivendicane una col suo id impianto MIMIT:
            </p>
          }
          <div class="mt-3 flex items-center gap-2">
            <input type="number" [(ngModel)]="claimId" placeholder="id impianto"
                   class="w-36 rounded-lg border border-slate-300 px-2 py-1 text-sm" />
            <button (click)="claim()" class="rounded-lg bg-brand-600 px-3 py-1 text-sm font-semibold text-white hover:bg-brand-700">
              Rivendica stazione
            </button>
            @if (claimError()) { <span class="text-xs text-rose-600">{{ claimError() }}</span> }
          </div>
        </div>

        @if (stationId) {
          <!-- Ranking -->
          <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
            <h2 class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Posizionamento in zona</h2>
            @if (ranking(); as r) {
              @if (r.myPrice !== null) {
                <div class="flex flex-wrap items-baseline gap-x-6 gap-y-1">
                  <p class="text-2xl font-bold text-slate-900">
                    {{ r.rank }}° <span class="text-base font-medium text-slate-500">su {{ r.total }}</span>
                  </p>
                  <p class="text-sm text-slate-600">Il tuo prezzo: <span class="font-semibold">{{ r.myPrice | number: '1.3-3' }} €</span></p>
                  <p class="text-sm" [class]="r.cheaperThanMe <= r.dearerThanMe ? 'text-brand-700' : 'text-rose-600'">
                    {{ r.cheaperThanMe }} più economici · {{ r.dearerThanMe }} più cari
                  </p>
                </div>
                <div class="mt-3 flex justify-between text-[11px] tabular-nums text-slate-400">
                  <span>min {{ r.localMin | number: '1.3-3' }}</span>
                  <span>mediana {{ r.localMedian | number: '1.3-3' }}</span>
                  <span>max {{ r.localMax | number: '1.3-3' }}</span>
                </div>
              } @else {
                <p class="text-sm text-slate-500">Nessun prezzo recente per questa stazione/carburante.</p>
              }
            }
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <!-- Competitor changes -->
            <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
              <h2 class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Variazioni recenti dei competitor</h2>
              @for (c of competitors(); track c.stationId + c.date) {
                <div class="flex items-center justify-between border-b border-slate-50 py-2 text-sm">
                  <div class="min-w-0">
                    <p class="truncate text-slate-800">{{ c.nome || c.bandiera }}</p>
                    <p class="text-[11px] text-slate-400">{{ c.date | date: 'dd/MM/yy' }}</p>
                  </div>
                  <div class="text-right tabular-nums">
                    <span class="text-slate-500">{{ c.previousPrice | number: '1.3-3' }} → </span>
                    <span class="font-semibold text-slate-900">{{ c.newPrice | number: '1.3-3' }}</span>
                    <span [class]="c.delta > 0 ? 'text-rose-600' : 'text-brand-700'"> ({{ c.delta > 0 ? '+' : '' }}{{ c.delta | number: '1.3-3' }})</span>
                  </div>
                </div>
              } @empty {
                <p class="py-4 text-center text-sm text-slate-500">Nessuna variazione nel periodo.</p>
              }
            </div>

            <!-- Movers / leadership -->
            <div class="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
              <h2 class="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">Chi muove di più il prezzo (attività)</h2>
              @for (m of movers(); track m.stationId) {
                <div class="flex items-center justify-between border-b border-slate-50 py-2 text-sm"
                     [class.bg-brand-50]="m.isMine">
                  <p class="min-w-0 truncate text-slate-800">
                    {{ m.nome || m.bandiera }} @if (m.isMine) { <span class="text-[11px] font-semibold text-brand-700">(tu)</span> }
                  </p>
                  <span class="tabular-nums text-slate-600">{{ m.changes }} cambi · Δ~{{ m.avgAbsDelta | number: '1.3-3' }}</span>
                </div>
              } @empty {
                <p class="py-4 text-center text-sm text-slate-500">Nessun dato nel periodo.</p>
              }
            </div>
          </div>
        }
      </main>
    </div>
  `,
})
export class ManagerDashboardComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly mgr = inject(ManagerService);
  private readonly router = inject(Router);

  readonly fuels = FUELS;
  fuel = 'Benzina';
  stationId: number | null = null;
  claimId: number | null = null;

  readonly self = signal(true);
  readonly radiusKm = signal(5);
  readonly stations = signal<ClaimedStation[]>([]);
  readonly ranking = signal<Ranking | null>(null);
  readonly competitors = signal<CompetitorChange[]>([]);
  readonly movers = signal<Mover[]>([]);
  readonly claimError = signal<string | null>(null);

  get selfModel() { return this.self(); }
  set selfModel(v: boolean) { this.self.set(v); }
  get radiusModel() { return this.radiusKm(); }
  set radiusModel(v: number) { this.radiusKm.set(Number(v)); }

  ngOnInit(): void {
    this.auth.me().subscribe({ error: () => {} });
    this.loadStations();
  }

  private loadStations(): void {
    this.mgr.myStations().subscribe((list) => {
      this.stations.set(list);
      if (list.length && this.stationId === null) {
        this.stationId = list[0].stationId;
        this.load();
      }
    });
  }

  load(): void {
    if (this.stationId === null) return;
    const id = Number(this.stationId);
    const r = this.radiusKm() * 1000;
    this.mgr.ranking(id, this.fuel, this.self(), r).subscribe((x) => this.ranking.set(x));
    this.mgr.competitors(id, this.fuel, this.self(), r).subscribe((x) => this.competitors.set(x));
    this.mgr.movers(id, this.fuel, this.self(), r).subscribe((x) => this.movers.set(x));
  }

  claim(): void {
    this.claimError.set(null);
    if (!this.claimId) return;
    this.mgr.claim(Number(this.claimId)).subscribe({
      next: () => { this.claimId = null; this.loadStations(); },
      error: (e) => this.claimError.set(e?.error?.message || 'Impossibile rivendicare'),
    });
  }

  logout(): void {
    this.auth.logout().subscribe({ next: () => this.router.navigate(['/manager/login']) });
  }
}
