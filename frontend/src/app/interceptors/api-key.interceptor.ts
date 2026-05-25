import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Intercepteur HTTP Angular — ajoute X-API-Key à toutes les requêtes /api/*.
 *
 * Note : dans un déploiement production complet, cette clé serait transmise
 * par le serveur du marchand via un token de session court-terme, jamais
 * codée directement dans le bundle frontend. Pour ce démo standalone, elle
 * est dans environment.ts.
 */
export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = environment.apiKey;

  if (!apiKey || !req.url.startsWith('/api/')) {
    return next(req);
  }

  const authReq = req.clone({
    setHeaders: { 'X-API-Key': apiKey }
  });

  return next(authReq);
};
