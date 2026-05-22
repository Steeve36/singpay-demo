import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subscription, interval } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';

interface Order {
  reference: string;
  amount: number;
  status: string;
  customerName: string;
  customerEmail: string;
  airtelMoneyId: string | null;
  createdAt: string;
  updatedAt: string;
}

@Component({
  selector: 'app-payment-success',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <section class="page">

      <!-- Chargement / attente webhook -->
      <div class="card" *ngIf="state === 'loading'">
        <div class="spinner"></div>
        <h2>Vérification du paiement…</h2>
        <p class="sub">Confirmation en cours, merci de patienter.</p>
      </div>

      <!-- Succès confirmé -->
      <div class="card" *ngIf="state === 'success' && order">
        <div class="icon success-icon">
          <svg viewBox="0 0 52 52" fill="none">
            <circle cx="26" cy="26" r="26" fill="#E6F5EE"/>
            <path d="M15 26.5L22.5 34L37 19" stroke="#0D7A4E" stroke-width="3"
                  stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>

        <h1>Paiement confirmé</h1>
        <p class="sub">Votre transaction a été validée avec succès.</p>

        <div class="receipt">
          <div class="receipt-row">
            <span>Référence</span>
            <strong>{{ order.reference }}</strong>
          </div>
          <div class="receipt-row">
            <span>Client</span>
            <strong>{{ order.customerName }}</strong>
          </div>
          <div class="receipt-row">
            <span>Email</span>
            <strong>{{ order.customerEmail }}</strong>
          </div>
          <div class="receipt-row amount-row">
            <span>Montant payé</span>
            <strong class="amount">{{ order.amount | number:'1.0-0' }} FCFA</strong>
          </div>
          <div class="receipt-row" *ngIf="order.airtelMoneyId">
            <span>ID transaction</span>
            <strong>{{ order.airtelMoneyId }}</strong>
          </div>
          <div class="receipt-row">
            <span>Date</span>
            <strong>{{ order.updatedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
          </div>
        </div>

        <a routerLink="/checkout" class="btn-primary">Nouveau paiement</a>
      </div>

      <!-- Timeout : webhook pas encore reçu après 30 s -->
      <div class="card" *ngIf="state === 'timeout'">
        <div class="icon warning-icon">
          <svg viewBox="0 0 52 52" fill="none">
            <circle cx="26" cy="26" r="26" fill="#FFF8E1"/>
            <path d="M26 16v12" stroke="#F59E0B" stroke-width="3" stroke-linecap="round"/>
            <circle cx="26" cy="35" r="2" fill="#F59E0B"/>
          </svg>
        </div>

        <h1>Paiement en cours</h1>
        <p class="sub">
          La confirmation prend plus de temps que prévu.<br>
          Si le paiement a bien été effectué, votre commande sera traitée automatiquement.
        </p>

        <div class="receipt" *ngIf="order">
          <div class="receipt-row">
            <span>Référence</span>
            <strong>{{ order.reference }}</strong>
          </div>
          <div class="receipt-row">
            <span>Statut actuel</span>
            <span class="badge-pending">En attente</span>
          </div>
        </div>

        <a routerLink="/checkout" class="btn-secondary">Retour à l'accueil</a>
      </div>

    </section>
  `,
  styles: [`
    .page {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #F7F6F2;
      padding: 2rem;
      font-family: 'DM Sans', system-ui, sans-serif;
    }

    .card {
      background: #fff;
      border-radius: 16px;
      border: 1px solid #E2E2E2;
      padding: 3rem 2.5rem;
      max-width: 460px;
      width: 100%;
      text-align: center;
    }

    /* Spinner */
    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #E2E2E2;
      border-top-color: #0D7A4E;
      border-radius: 50%;
      animation: spin 0.9s linear infinite;
      margin: 0 auto 1.5rem;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .icon { margin-bottom: 1.5rem; svg { width: 64px; height: 64px; } }

    h1 {
      font-size: 1.6rem;
      font-weight: 700;
      color: #1A1A1A;
      margin: 0 0 0.5rem;
    }

    h2 {
      font-size: 1.2rem;
      font-weight: 600;
      color: #1A1A1A;
      margin: 0 0 0.5rem;
    }

    .sub {
      font-size: 0.9rem;
      color: #6B6B6B;
      margin-bottom: 2rem;
      line-height: 1.6;
    }

    .receipt {
      text-align: left;
      background: #F7F6F2;
      border-radius: 10px;
      padding: 0.25rem 1.25rem;
      margin-bottom: 2rem;
    }

    .receipt-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.65rem 0;
      border-bottom: 1px solid #E2E2E2;
      font-size: 0.85rem;
      gap: 1rem;

      &:last-child { border-bottom: none; }

      span:first-child { color: #6B6B6B; white-space: nowrap; }
      strong { font-weight: 500; text-align: right; word-break: break-all; }
    }

    .amount { color: #0D7A4E; font-size: 1.05rem !important; font-weight: 600 !important; }

    .badge-pending {
      background: #FFF8E1;
      color: #B45309;
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 500;
    }

    .btn-primary {
      display: inline-block;
      padding: 0.8rem 2.5rem;
      background: #1A1A1A;
      color: #fff;
      border-radius: 8px;
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: background 0.15s;
      &:hover { background: #333; }
    }

    .btn-secondary {
      display: inline-block;
      padding: 0.8rem 2.5rem;
      background: transparent;
      color: #1A1A1A;
      border: 1.5px solid #E2E2E2;
      border-radius: 8px;
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: border-color 0.15s;
      &:hover { border-color: #1A1A1A; }
    }
  `]
})
export class PaymentSuccessComponent implements OnInit, OnDestroy {

  state: 'loading' | 'success' | 'timeout' = 'loading';
  order: Order | null = null;

  private readonly MAX_POLLS = 15;
  private pollCount = 0;
  private pollSub: Subscription | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const ref = this.route.snapshot.queryParamMap.get('reference');
    if (!ref) { this.router.navigate(['/checkout']); return; }
    this.startPolling(ref);
  }

  private startPolling(ref: string): void {
    this.pollSub = interval(2000).pipe(
      startWith(0),
      switchMap(() => this.http.get<Order>(`/api/payment/order/${ref}`))
    ).subscribe({
      next: (order) => {
        this.order = order;
        this.pollCount++;

        if (order.status === 'PAID') {
          this.state = 'success';
          this.stop();
        } else if (order.status.startsWith('FAILED_') || order.status === 'FRAUD_SUSPECTED') {
          this.stop();
          this.router.navigate(['/paiement/echec'], {
            queryParams: { reference: ref, error: order.status }
          });
        } else if (this.pollCount >= this.MAX_POLLS) {
          this.state = 'timeout';
          this.stop();
        }
      },
      error: () => { this.state = 'timeout'; this.stop(); }
    });
  }

  private stop(): void {
    this.pollSub?.unsubscribe();
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
