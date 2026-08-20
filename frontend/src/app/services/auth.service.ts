import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap, switchMap } from 'rxjs';
import { Router } from '@angular/router';

export interface AuthUser {
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _token = signal<string | null>(null);
  private readonly _user  = signal<AuthUser | null>(null);

  // Used by JwtInterceptor to serialize concurrent refresh calls
  private _refreshing = false;
  private _refreshSubject = new BehaviorSubject<string | null>(null);

  readonly isLoggedIn  = computed(() => this._token() !== null);
  readonly currentUser = this._user.asReadonly();

  constructor(private http: HttpClient, private router: Router) {}

  getToken(): string | null {
    return this._token();
  }

  isRefreshing(): boolean { return this._refreshing; }

  getRefreshSubject(): BehaviorSubject<string | null> { return this._refreshSubject; }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(tap(res => this.storeAuth(res)));
  }

  register(firstName: string, lastName: string, email: string, password: string): Observable<{ message: string }> {
    return this.http
      .post<{ message: string }>('/api/auth/register', { firstName, lastName, email, password });
  }

  doRefresh(): Observable<AuthResponse> {
    this._refreshing = true;
    this._refreshSubject.next(null);
    return this.http
      .post<AuthResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .pipe(
        tap(res => {
          this.storeAuth(res);
          this._refreshing = false;
          this._refreshSubject.next(res.accessToken);
        })
      );
  }

  logout(): void {
    this.http.post('/api/auth/logout', {}, { withCredentials: true }).subscribe();
    this.clearAuth();
    this.router.navigate(['/login']);
  }

  private storeAuth(res: AuthResponse): void {
    this._token.set(res.accessToken);
    this._user.set({
      userId:    res.userId,
      email:     res.email,
      firstName: res.firstName,
      lastName:  res.lastName,
      role:      res.role
    });
  }

  clearAuth(): void {
    this._token.set(null);
    this._user.set(null);
    this._refreshing = false;
    this._refreshSubject.next(null);
  }
}
