import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-manager-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="flex min-h-[100dvh] items-center justify-center bg-slate-100 p-4 font-sans">
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl ring-1 ring-slate-200">
        <div class="mb-4 flex items-center gap-2">
          <span class="text-xl">⛽</span>
          <div>
            <h1 class="text-base font-bold leading-none text-slate-900">fuel-cast</h1>
            <p class="text-[11px] text-slate-500">area gestori</p>
          </div>
        </div>

        <h2 class="mb-3 text-lg font-semibold text-slate-800">{{ mode() === 'login' ? 'Accedi' : 'Registrati' }}</h2>

        @if (mode() === 'register') {
          <input [(ngModel)]="displayName" placeholder="Nome" class="mb-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        }
        <input [(ngModel)]="email" type="email" placeholder="Email" autocomplete="username"
               class="mb-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <input [(ngModel)]="password" type="password" placeholder="Password" autocomplete="current-password"
               class="mb-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm" />

        @if (error()) {
          <p class="mb-3 text-sm text-rose-600">{{ error() }}</p>
        }

        <button (click)="submit()" [disabled]="busy()"
                class="w-full rounded-lg bg-brand-600 py-2 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:opacity-60">
          {{ mode() === 'login' ? 'Accedi' : 'Crea account' }}
        </button>

        <button (click)="toggle()" class="mt-2 w-full text-center text-xs text-brand-700 hover:underline">
          {{ mode() === 'login' ? 'Non hai un account? Registrati' : 'Hai già un account? Accedi' }}
        </button>

        <div class="mt-4 rounded-lg bg-slate-50 p-3 text-center text-xs text-slate-500">
          Prova la demo:
          <button (click)="loginDemo()" class="font-semibold text-brand-700 hover:underline">
            entra come gestore demo
          </button>
        </div>

        <a routerLink="/" class="mt-3 block text-center text-xs text-slate-400 hover:underline">← torna alla mappa</a>
      </div>
    </div>
  `,
})
export class ManagerLoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly mode = signal<'login' | 'register'>('login');
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  email = '';
  password = '';
  displayName = '';

  ngOnInit(): void {
    // Prime the XSRF-TOKEN cookie so the first POST passes CSRF, and skip login
    // if a session already exists.
    this.auth.me().subscribe({ next: () => this.router.navigate(['/manager']), error: () => {} });
  }

  toggle(): void {
    this.mode.set(this.mode() === 'login' ? 'register' : 'login');
    this.error.set(null);
  }

  submit(): void {
    this.error.set(null);
    this.busy.set(true);
    const done = { next: () => this.router.navigate(['/manager']), error: (e: any) => this.fail(e) };
    if (this.mode() === 'login') {
      this.auth.login(this.email.trim(), this.password).subscribe(done);
    } else {
      this.auth.register(this.email.trim(), this.password, this.displayName.trim()).subscribe(done);
    }
  }

  loginDemo(): void {
    this.email = 'demo@fuelcast.it';
    this.password = 'demo-fuelcast';
    this.mode.set('login');
    this.submit();
  }

  private fail(e: any): void {
    this.busy.set(false);
    this.error.set(e?.error?.message || (e?.status === 401 ? 'Email o password non validi' : 'Errore, riprova'));
  }
}
