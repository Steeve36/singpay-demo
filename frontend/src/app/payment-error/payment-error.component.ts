import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';

interface Order {
  reference: string;
  amount: number;
  status: string;
  customerName: string;
  customerEmail: string;
  createdAt: string;
}

const ERROR_MESSAGES: Record<string, string> = {
  'FAILED_BalanceError':  'Solde insuffisant sur votre compte Mobile Money.',
  'FAILED_PasswordError': 'PIN incorrect. Veuillez réessayer.',
  'FAILED_TimeOutError':  'Délai de confirmation dépassé. La transaction a été annulée.',
  'FAILED_UserCancelled': 'Paiement annulé par l\'utilisateur.',
  'FRAUD_SUSPECTED':      'Transaction bloquée pour raison de sécurité. Contactez le support.',
};

@Component({
  selector: 'app-payment-error',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <section class="page">
      <div class="card">

        <div class="icon">
          <svg viewBox="0 0 52 52" fill="none">
            <circle cx="26" cy="26" r="26" fill="#FEF2F2"/>
            <path d="M17 17L35 35M35 17L17 35" stroke="#DC2626" stroke-width="3"
                  stroke-linecap="round"/>
          </svg>
        </div>

        <h1>Paiement échoué</h1>
        <p class="sub">{{ errorMessage }}</p>

        <div class="receipt" *ngIf="order">
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
            <span>Montant</span>
            <strong class="amount">{{ order.amount | number:'1.0-0' }} FCFA</strong>
          </div>
          <div class="receipt-row">
            <span>Date</span>
            <strong>{{ order.createdAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
          </div>
          <div class="receipt-row">
            <span>Statut</span>
            <span class="badge-error">Échec</span>
          </div>
        </div>

        <div class="receipt skeleton" *ngIf="!order && reference">
          <div class="receipt-row">
            <span>Référence</span>
            <strong>{{ reference }}</strong>
          </div>
        </div>

        <div class="actions">
          <a routerLink="/checkout" class="btn-retry">Réessayer</a>
          <a routerLink="/" class="btn-secondary">Accueil</a>
        </div>

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

    .icon { margin-bottom: 1.5rem; svg { width: 64px; height: 64px; } }

    h1 {
      font-size: 1.6rem;
      font-weight: 700;
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

    .amount { color: #DC2626; font-size: 1.05rem !important; font-weight: 600 !important; }

    .badge-error {
      background: #FEF2F2;
      color: #DC2626;
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 500;
    }

    .actions {
      display: flex;
      gap: 10px;
      justify-content: center;
    }

    .btn-retry {
      padding: 0.8rem 1.8rem;
      background: #DC2626;
      color: #fff;
      border-radius: 8px;
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
      transition: background 0.15s;
      &:hover { background: #B91C1C; }
    }

    .btn-secondary {
      padding: 0.8rem 1.8rem;
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
export class PaymentErrorComponent implements OnInit {

  order: Order | null = null;
  reference = '';
  errorMessage = 'Une erreur est survenue lors du paiement.';

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.reference = this.route.snapshot.queryParamMap.get('reference') ?? '';
    const errorCode = this.route.snapshot.queryParamMap.get('error') ?? '';

    if (errorCode) {
      this.errorMessage = ERROR_MESSAGES[errorCode] ?? this.errorMessage;
    }

    if (this.reference) {
      this.http.get<Order>(`/api/payment/order/${this.reference}`)
        .subscribe({
          next: (order) => {
            this.order = order;
            if (!errorCode && order.status) {
              this.errorMessage = ERROR_MESSAGES[order.status] ?? this.errorMessage;
            }
          }
        });
    }
  }
}
