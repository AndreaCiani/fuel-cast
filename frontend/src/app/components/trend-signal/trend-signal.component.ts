import { Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';

import { LocalTrend } from '../../models/station.model';

/** "Fill up now or wait?" signal: verdict + delta + sparkline of local averages. */
@Component({
  selector: 'app-trend-signal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (visible()) {
      <div class="rounded-xl border p-3" [class]="boxClass()">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span class="text-lg leading-none">{{ icon() }}</span>
            <span class="text-sm font-semibold">{{ headline() }}</span>
          </div>
          @if (deltaLabel()) {
            <span class="tabular-nums text-xs font-medium">{{ deltaLabel() }}</span>
          }
        </div>
        <svg viewBox="0 0 240 36" class="mt-2 h-9 w-full" preserveAspectRatio="none">
          <polyline
            [attr.points]="sparkPoints()"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linejoin="round"
            stroke-linecap="round"
          />
        </svg>
        <p class="mt-1 text-[11px] opacity-70">
          media ultimi giorni vs precedenti · {{ trend()!.points.length }} giorni, {{ (trend()!.radiusMeters / 1000) }} km
        </p>
      </div>
    }
  `,
})
export class TrendSignalComponent {
  readonly trend = input<LocalTrend | null>(null);

  readonly visible = computed(() => {
    const t = this.trend();
    return !!t && t.direction !== 'INSUFFICIENT' && t.points.length >= 2;
  });

  readonly icon = computed(() => {
    switch (this.trend()?.direction) {
      case 'RISING': return '↑';
      case 'FALLING': return '↓';
      default: return '→';
    }
  });

  readonly headline = computed(() => {
    switch (this.trend()?.direction) {
      case 'RISING': return 'Prezzi in salita — conviene fare il pieno ora';
      case 'FALLING': return 'Prezzi in calo — puoi aspettare';
      default: return 'Prezzi stabili in zona';
    }
  });

  readonly boxClass = computed(() => {
    switch (this.trend()?.direction) {
      case 'RISING': return 'border-rose-200 bg-rose-50 text-rose-700';
      case 'FALLING': return 'border-brand-200 bg-brand-50 text-brand-700';
      default: return 'border-slate-200 bg-slate-50 text-slate-600';
    }
  });

  readonly deltaLabel = computed(() => {
    const d = this.trend()?.deltaPerLitre;
    if (d === null || d === undefined) return '';
    const sign = d > 0 ? '+' : '';
    return `${sign}${d.toFixed(3)} €/L`;
  });

  readonly sparkPoints = computed(() => {
    const pts = this.trend()?.points ?? [];
    if (pts.length < 2) return '';
    const w = 240, h = 36, pad = 3;
    const prices = pts.map((p) => p.avgPrice);
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const span = max - min || 1;
    return pts
      .map((p, i) => {
        const x = pad + (i * (w - 2 * pad)) / (pts.length - 1);
        const y = h - pad - ((p.avgPrice - min) / span) * (h - 2 * pad);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });
}
