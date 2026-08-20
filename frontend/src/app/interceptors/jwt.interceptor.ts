import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, filter, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register'];

export const jwtInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  // Auth endpoints: attach withCredentials but no Bearer token
  if (AUTH_PATHS.some(p => req.url.includes(p))) {
    return next(req.clone({ withCredentials: true }));
  }

  const authReq = addToken(req, auth.getToken());

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || req.url.includes('/api/auth/')) {
        return throwError(() => err);
      }

      // Token expired — attempt refresh
      if (auth.isRefreshing()) {
        // Another request already triggered a refresh; wait for the new token
        return auth.getRefreshSubject().pipe(
          filter(token => token !== null),
          take(1),
          switchMap(token => next(addToken(req, token)))
        );
      }

      return auth.doRefresh().pipe(
        switchMap(res => next(addToken(req, res.accessToken))),
        catchError(refreshErr => {
          auth.clearAuth();
          router.navigate(['/login']);
          return throwError(() => refreshErr);
        })
      );
    })
  );
};

function addToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) return req.clone({ withCredentials: true });
  return req.clone({
    withCredentials: true,
    setHeaders: { Authorization: `Bearer ${token}` }
  });
}
