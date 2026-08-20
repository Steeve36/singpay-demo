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
    <div class="layout">

      <!-- Header -->
      <header class="header">
        <div class="brand">
          <span class="brand-mark"></span>
          <span class="brand-name">QTZ-App</span>
        </div>
        <div class="header-secure">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.5" stroke-linecap="square">
            <rect x="3" y="11" width="18" height="11" rx="0"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
          Paiement sécurisé
        </div>
      </header>

      <!-- Contenu -->
      <main class="content">
        <div class="card">

          <div class="status-badge error-badge">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.5" stroke-linecap="square" stroke-linejoin="miter">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
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
            <div class="receipt-row">
              <span>Montant</span>
              <strong class="amount">{{ order.amount | number:'1.0-0' }} FCFA</strong>
            </div>
            <div class="receipt-row">
              <span>Date</span>
              <strong>{{ order.createdAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
            </div>
            <div class="receipt-row">
              <span>Statut</span>
              <span class="badge badge-error">Échec</span>
            </div>
          </div>

          <div class="receipt skeleton" *ngIf="!order && reference">
            <div class="receipt-row">
              <span>Référence</span>
              <strong>{{ reference }}</strong>
            </div>
          </div>

          <div class="actions">
            <a routerLink="/checkout" class="btn-primary">Réessayer</a>
            <a routerLink="/catalogue" class="btn-secondary">Accueil</a>
          </div>

        </div>
      </main>

      <!-- Footer -->
      <footer class="footer">
        <span>© 2025 QTZ-App</span>
        <span class="sep"></span>
        <span>Paiement sécurisé par SingPay</span>
      </footer>

    </div>
  `,
  styles: [`
    .layout {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background: #FFFFFF;
      font-family: 'Inter', sans-serif;
      color: #1A1A1A;
    }

    /* ── Header ─────────────────────────────────────────────── */
    .header {
      height: 60px;
      background: #FFFFFF;
      box-shadow: 0 1px 6px rgba(0,0,0,0.08);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 2rem;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .brand-mark {
      width: 24px;
      height: 24px;
      background: #00653C;
      flex-shrink: 0;
    }

    .brand-name {
      font-weight: 700;
      font-size: 1rem;
      color: #1A1A1A;
    }

    .header-secure {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 500;
      color: #555555;
      letter-spacing: 0.03em;
    }

    /* ── Content ─────────────────────────────────────────────── */
    .content {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 3rem 1.5rem;
      background: #F5F5F5;
    }

    .card {
      background: #FFFFFF;
      border: 1px solid #E0E0E0;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      padding: 2.75rem 2.25rem;
      max-width: 460px;
      width: 100%;
      text-align: center;
    }

    /* ── Status badge ────────────────────────────────────────── */
    .status-badge {
      width: 56px;
      height: 56px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 1.5rem;
    }

    .error-badge {
      background: #FEF2F2;
      color: #DC2626;
    }

    /* ── Typography ──────────────────────────────────────────── */
    h1 {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1A1A1A;
      margin: 0 0 0.5rem;
      letter-spacing: -0.02em;
    }

    .sub {
      font-size: 0.88rem;
      color: #555555;
      margin-bottom: 2rem;
      line-height: 1.6;
    }

    /* ── Receipt ─────────────────────────────────────────────── */
    .receipt {
      text-align: left;
      background: #FEF2F2;
      border: 1px solid #E0E0E0;
      padding: 0 1.25rem;
      margin-bottom: 2rem;
    }

    .receipt-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.7rem 0;
      border-bottom: 1px solid #E0E0E0;
      font-size: 0.83rem;
      gap: 1rem;
    }

    .receipt-row:last-child { border-bottom: none; }

    .receipt-row span:first-child {
      color: #555555;
      white-space: nowrap;
    }

    .receipt-row strong {
      font-weight: 500;
      text-align: right;
      word-break: break-all;
      color: #1A1A1A;
    }

    .amount {
      color: #DC2626 !important;
      font-weight: 600 !important;
    }

    /* ── Badges ──────────────────────────────────────────────── */
    .badge {
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      padding: 4px 10px;
    }

    .badge-error {
      background: #FEF2F2;
      color: #DC2626;
      border: 1px solid #FECACA;
    }

    /* ── Actions ─────────────────────────────────────────────── */
    .actions {
      display: flex;
      gap: 10px;
      justify-content: center;
      flex-wrap: wrap;
    }

    .btn-primary {
      display: inline-block;
      padding: 0.85rem 2rem;
      background: #00653C;
      color: #FFFFFF;
      border: 2px solid #00653C;
      text-decoration: none;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      transition: background 0.15s;
      font-family: 'Inter', sans-serif;
    }

    .btn-primary:hover { background: #004d2d; border-color: #004d2d; }

    .btn-secondary {
      display: inline-block;
      padding: 0.85rem 2rem;
      background: transparent;
      color: #00653C;
      border: 2px solid #00653C;
      text-decoration: none;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      transition: background 0.15s, color 0.15s;
      font-family: 'Inter', sans-serif;
    }

    .btn-secondary:hover {
      background: #00653C;
      color: #FFFFFF;
    }

    /* ── Footer ──────────────────────────────────────────────── */
    .footer {
      background: #FFFFFF;
      border-top: 1px solid #E0E0E0;
      padding: 1.25rem 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1.5rem;
    }

    .footer span {
      font-size: 0.72rem;
      color: #555555;
    }

    .sep {
      width: 1px;
      height: 12px;
      background: #E0E0E0 !important;
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
