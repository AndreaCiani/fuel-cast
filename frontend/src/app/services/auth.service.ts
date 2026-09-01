import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { Manager } from '../models/manager.model';

/** Station-manager authentication (session cookie + CSRF handled by HttpClient). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /** Current manager, or null if not logged in. */
  readonly manager = signal<Manager | null>(null);

  me(): Observable<Manager> {
    return this.http.get<Manager>('/api/auth/me').pipe(tap((m) => this.manager.set(m)));
  }

  /** Fire-and-forget GET to make the backend set the XSRF-TOKEN cookie before a POST. */
  primeCsrf(): void {
    this.http.get('/api/auth/me', { responseType: 'text' }).subscribe({ next: () => {}, error: () => {} });
  }

  login(email: string, password: string): Observable<Manager> {
    return this.http.post<Manager>('/api/auth/login', { email, password }).pipe(tap((m) => this.manager.set(m)));
  }

  register(email: string, password: string, displayName: string): Observable<Manager> {
    return this.http
      .post<Manager>('/api/auth/register', { email, password, displayName })
      .pipe(tap((m) => this.manager.set(m)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(tap(() => this.manager.set(null)));
  }
}
