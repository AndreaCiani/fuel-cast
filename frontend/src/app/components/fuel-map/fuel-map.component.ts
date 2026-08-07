import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import maplibregl, { Map as MlMap, Marker } from 'maplibre-gl';

import { NearbyStation } from '../../models/station.model';

/** MapLibre wrapper: renders price-coloured station markers over a base map. */
@Component({
  selector: 'app-fuel-map',
  standalone: true,
  template: `
    <div class="relative h-full w-full">
      <div #map class="h-full w-full"></div>
      <!-- Search-centre crosshair -->
      <div class="pointer-events-none absolute inset-0 flex items-center justify-center">
        <div class="h-3 w-3 rounded-full bg-brand-600 ring-4 ring-brand-600/25"></div>
      </div>
    </div>
  `,
})
export class FuelMapComponent implements AfterViewInit, OnDestroy {
  private readonly mapEl = viewChild.required<ElementRef<HTMLDivElement>>('map');

  readonly stations = input<NearbyStation[]>([]);
  readonly selectedId = input<number | null>(null);
  readonly initialCenter = input<{ lat: number; lon: number }>({ lat: 41.9028, lon: 12.4964 });

  readonly markerSelect = output<number>();

  private map?: MlMap;
  private markers: Marker[] = [];

  private readonly priceRange = computed(() => {
    const prices = this.stations().map((s) => s.price);
    return prices.length ? { min: Math.min(...prices), max: Math.max(...prices) } : null;
  });

  constructor() {
    // Re-render markers whenever the station list or selection changes.
    effect(() => {
      this.stations();
      this.selectedId();
      if (this.map) this.renderMarkers();
    });
  }

  ngAfterViewInit(): void {
    const c = this.initialCenter();
    this.map = new maplibregl.Map({
      container: this.mapEl().nativeElement,
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [c.lon, c.lat],
      zoom: 12,
      attributionControl: { compact: true },
    });
    this.map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right');
    this.map.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), 'bottom-right');
    this.map.on('load', () => this.renderMarkers());
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  /** Current map centre, for "search this area". */
  getCenter(): { lat: number; lon: number } {
    const c = this.map?.getCenter();
    return c ? { lat: c.lat, lon: c.lng } : this.initialCenter();
  }

  flyTo(lat: number, lon: number): void {
    this.map?.flyTo({ center: [lon, lat], zoom: 14 });
  }

  private renderMarkers(): void {
    if (!this.map) return;
    this.markers.forEach((m) => m.remove());
    this.markers = [];

    const range = this.priceRange();
    const selected = this.selectedId();

    for (const s of this.stations()) {
      const el = document.createElement('button');
      el.className =
        'rounded-full px-2 py-0.5 text-xs font-semibold text-white shadow-md ring-2 ring-white cursor-pointer transition-transform';
      el.style.backgroundColor = this.colorFor(s.price, range);
      el.textContent = s.price.toFixed(3);
      if (s.id === selected) {
        el.style.transform = 'scale(1.25)';
        el.style.zIndex = '10';
        el.classList.remove('ring-white');
        el.classList.add('ring-brand-700');
      }
      el.addEventListener('click', (ev) => {
        ev.stopPropagation();
        this.markerSelect.emit(s.id);
      });
      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([s.longitude, s.latitude])
        .addTo(this.map);
      this.markers.push(marker);
    }
  }

  /** Green (cheapest) → red (dearest) across the current result set. */
  private colorFor(price: number, range: { min: number; max: number } | null): string {
    if (!range || range.max === range.min) return '#059669';
    const ratio = (price - range.min) / (range.max - range.min);
    const hue = 140 * (1 - ratio); // 140=green, 0=red
    return `hsl(${hue}, 72%, 42%)`;
  }
}
