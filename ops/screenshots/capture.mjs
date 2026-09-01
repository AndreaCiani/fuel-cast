// Generates README screenshots of the running fuel-cast app.
// Usage: APP_URL=http://localhost:8090 npm run shoot
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

const APP_URL = process.env.APP_URL || 'http://localhost:8090';
const OUT = resolve(process.cwd(), '../../docs/screenshots');
mkdirSync(OUT, { recursive: true });

// Software WebGL so MapLibre's base tiles render in headless. Recent Chromium
// gates the swiftshader fallback behind --enable-unsafe-swiftshader.
const browser = await chromium.launch({
  args: [
    '--enable-unsafe-swiftshader',
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--ignore-gpu-blocklist',
    '--enable-webgl',
  ],
});

async function shoot(name, { width, height, isMobile = false, openDetail = false }) {
  const ctx = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: 2,
    isMobile,
    hasTouch: isMobile,
    // Deny geolocation so the app falls back to its default (Rome) and searches.
    permissions: [],
  });
  const page = await ctx.newPage();
  await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
  // Wait for the search results to arrive, then let map tiles + markers settle.
  await page.getByText('stazioni', { exact: false }).first().waitFor({ timeout: 20000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(7000);

  if (openDetail) {
    const station = page.getByRole('button').filter({ hasText: 'agg.' }).first();
    await station.click().catch(() => {});
    await page.getByText('rispetto agli ultimi', { exact: false }).first().waitFor({ timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(1500);
  }

  const file = resolve(OUT, `${name}.png`);
  await page.screenshot({ path: file });
  console.log('wrote', file);
  await ctx.close();
}

await shoot('explore', { width: 1280, height: 800 });
await shoot('station-detail', { width: 1280, height: 800, openDetail: true });
await shoot('mobile', { width: 390, height: 844, isMobile: true });

// Side B — the station-manager dashboard, via the one-click demo login.
async function shootDashboard() {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  await page.goto(APP_URL + '/manager/login', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: /gestore demo/i }).click().catch(() => {});
  await page.getByText('POSIZIONAMENTO', { exact: false }).waitFor({ timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(3000);
  await page.screenshot({ path: resolve(OUT, 'manager-dashboard.png') });
  console.log('wrote', resolve(OUT, 'manager-dashboard.png'));
  await ctx.close();
}
await shootDashboard();

await browser.close();
console.log('done');
