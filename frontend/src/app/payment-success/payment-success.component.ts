import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subscription, interval } from 'rxjs';
import { startWith, switchMap } from 'rxjs/operators';
import { PaymentService } from '../services/payment.service';

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

        <!-- Chargement -->
        <div class="card" *ngIf="state === 'loading'">
          <div class="loader">
            <div class="loader-bar"></div>
          </div>
          <h2>Vérification du paiement…</h2>
          <p class="sub">Confirmation en cours, merci de patienter.</p>
        </div>

        <!-- Succès -->
        <div class="card" *ngIf="state === 'success' && order">
          <div class="status-badge success-badge">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.5" stroke-linecap="square" stroke-linejoin="miter">
              <polyline points="20 6 9 17 4 12"/>
            </svg>
          </div>

          <h1>Paiement confirmé</h1>
          <p class="sub">Votre transaction a été validée avec succès.</p>

          <div class="receipt">
            <div class="receipt-row">
              <span>Référence</span>
              <strong>{{ order.reference }}</strong>
            </div>
            <div class="receipt-row" *ngIf="order.customerName">
              <span>Client</span>
              <strong>{{ order.customerName }}</strong>
            </div>
            <div class="receipt-row" *ngIf="order.customerEmail">
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
            <div class="receipt-row" *ngIf="order.updatedAt">
              <span>Date</span>
              <strong>{{ order.updatedAt | date:'dd/MM/yyyy à HH:mm' }}</strong>
            </div>
          </div>

          <a routerLink="/" class="btn-primary">Retour à l'accueil</a>
        </div>

        <!-- Timeout / En attente -->
        <div class="card" *ngIf="state === 'timeout'">
          <div class="status-badge pending-badge">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.5" stroke-linecap="square">
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
              <rect x="2" y="2" width="20" height="20" rx="0"/>
            </svg>
          </div>

          <h1>Paiement en cours</h1>
          <p class="sub">
            La confirmation prend plus de temps que prévu.<br>
            Si le paiement a été effectué, votre commande sera traitée automatiquement.
          </p>

          <div class="receipt" *ngIf="order">
            <div class="receipt-row">
              <span>Référence</span>
              <strong>{{ order.reference }}</strong>
            </div>
            <div class="receipt-row">
              <span>Statut</span>
              <span class="badge badge-pending">En attente</span>
            </div>
          </div>

          <a routerLink="/" class="btn-secondary">Retour à l'accueil</a>
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

    /* ── Loader ──────────────────────────────────────────────── */
    .loader {
      width: 48px;
      height: 4px;
      background: #E0E0E0;
      margin: 0 auto 2rem;
      overflow: hidden;
      position: relative;
    }

    .loader-bar {
      position: absolute;
      height: 100%;
      width: 40%;
      background: #00653C;
      animation: loading 1.2s ease-in-out infinite;
    }

    @keyframes loading {
      0%   { left: -40%; }
      100% { left: 100%; }
    }

    /* ── Status badges ───────────────────────────────────────── */
    .status-badge {
      width: 56px;
      height: 56px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 1.5rem;
    }

    .success-badge { background: #CEFFEB; color: #00653C; }
    .pending-badge { background: #FFF8E1; color: #FFC900; }

    /* ── Typography ──────────────────────────────────────────── */
    h1 {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1A1A1A;
      margin: 0 0 0.5rem;
      letter-spacing: -0.02em;
    }

    h2 {
      font-size: 1.1rem;
      font-weight: 600;
      color: #1A1A1A;
      margin: 0 0 0.5rem;
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
      background: #F7FFF9;
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
      font-weight: 400;
    }

    .receipt-row strong {
      font-weight: 500;
      text-align: right;
      word-break: break-all;
      color: #1A1A1A;
    }

    .amount {
      color: #00653C !important;
      font-size: 1rem !important;
      font-weight: 700 !important;
    }

    /* ── Badges ──────────────────────────────────────────────── */
    .badge {
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      padding: 4px 10px;
    }

    .badge-pending { background: #FFC900; color: #1A1A1A; }

    /* ── Buttons ─────────────────────────────────────────────── */
    .btn-primary {
      display: inline-block;
      padding: 0.85rem 2.5rem;
      background: #00653C;
      color: #FFFFFF;
      border: none;
      text-decoration: none;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      transition: background 0.15s;
      font-family: 'Inter', sans-serif;
    }

    .btn-primary:hover { background: #004d2d; }

    .btn-secondary {
      display: inline-block;
      padding: 0.85rem 2.5rem;
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

    .btn-secondary:hover { background: #00653C; color: #FFFFFF; }

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

    .footer span { font-size: 0.72rem; color: #555555; }

    .sep {
      width: 1px;
      height: 12px;
      background: #E0E0E0 !important;
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
    private http: HttpClient,
    private paymentService: PaymentService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const txnId  = params.get('txnId');
    const ref    = params.get('reference');

    if (txnId) {
      // Flow Stripe : on vérifie en live puis on poll la DB
      this.startStripeFlow(Number(txnId), ref ?? '');
    } else if (ref) {
      // Flow Mobile Money : polling via l'ancien endpoint /api/payment/order/{ref}
      this.startMobileMoneyPolling(ref);
    } else {
      this.router.navigate(['/']);
    }
  }

  // ── Flow Stripe ────────────────────────────────────────────────────────────

  private startStripeFlow(txnId: number, ref: string): void {
    // Tente d'abord une vérification live pour mettre à jour la DB
    this.paymentService.verifyPayment(txnId).subscribe({
      next:  (res) => this.handleGatewayStatus(res, txnId, ref),
      error: ()    => this.startGatewayPolling(txnId, ref)
    });
  }

  private handleGatewayStatus(
    res: { status: string; orderReference?: string; amount?: number;
           customerName?: string; customerEmail?: string; updatedAt?: string },
    txnId: number,
    ref: string
  ): void {
    const savedState = this.readSavedState();

    if (res.status === 'SUCCESS') {
      this.order = {
        reference:    res.orderReference ?? ref,
        amount:       res.amount ?? savedState?.amount ?? 0,
        status:       res.status,
        customerName: res.customerName ?? savedState?.customerName ?? '',
        customerEmail:res.customerEmail ?? savedState?.customerEmail ?? '',
        airtelMoneyId: null,
        createdAt:    res.updatedAt ?? '',
        updatedAt:    res.updatedAt ?? ''
      };
      this.state = 'success';
      sessionStorage.removeItem('checkout_state_card');
      sessionStorage.removeItem('card_idempotency_key');
    } else if (res.status === 'FAILED') {
      this.router.navigate(['/paiement/echec'], {
        queryParams: { reference: ref, error: res.status }
      });
    } else {
      // PENDING/PROCESSING → on continue à poller
      this.startGatewayPolling(txnId, ref);
    }
  }

  private startGatewayPolling(txnId: number, ref: string): void {
    const savedState = this.readSavedState();

    this.pollSub = interval(2000).pipe(
      startWith(0),
      switchMap(() => this.paymentService.getTransactionStatus(txnId))
    ).subscribe({
      next: (res) => {
        this.pollCount++;

        if (res.status === 'SUCCESS') {
          this.order = {
            reference:    res.orderReference ?? ref,
            amount:       res.amount ?? savedState?.amount ?? 0,
            status:       res.status,
            customerName: res.customerName ?? savedState?.customerName ?? '',
            customerEmail:res.customerEmail ?? savedState?.customerEmail ?? '',
            airtelMoneyId: null,
            createdAt:    res.updatedAt ?? '',
            updatedAt:    res.updatedAt ?? ''
          };
          this.state = 'success';
          this.stop();
          sessionStorage.removeItem('checkout_state_card');
          sessionStorage.removeItem('card_idempotency_key');
        } else if (res.status === 'FAILED') {
          this.stop();
          this.router.navigate(['/paiement/echec'], {
            queryParams: { reference: ref, error: res.status }
          });
        } else if (this.pollCount >= this.MAX_POLLS) {
          this.order = { reference: ref, amount: savedState?.amount ?? 0,
                         status: 'PENDING', customerName: '', customerEmail: '',
                         airtelMoneyId: null, createdAt: '', updatedAt: '' };
          this.state = 'timeout';
          this.stop();
        }
      },
      error: () => { this.state = 'timeout'; this.stop(); }
    });
  }

  // ── Flow Mobile Money (inchangé) ───────────────────────────────────────────

  private startMobileMoneyPolling(ref: string): void {
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

  // ── Helpers ────────────────────────────────────────────────────────────────

  private readSavedState(): { amount: number; customerName: string; customerEmail: string } | null {
    try {
      const raw = sessionStorage.getItem('checkout_state_card');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  private stop(): void {
    this.pollSub?.unsubscribe();
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
