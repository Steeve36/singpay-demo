import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { CheckoutState, CheckoutStateService } from '../../services/checkout-state.service';

@Component({
  selector: 'app-payment-bank-transfer',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <section class="pay-page">

      <header class="pay-header">
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

      <div class="pay-body">
        <div class="transfer-card">

          <div class="transfer-icon" aria-hidden="true">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.5" stroke-linecap="square">
              <line x1="3" y1="22" x2="21" y2="22"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <polyline points="12 2 3 7 21 7"/>
              <line x1="6" y1="12" x2="6" y2="22"/>
              <line x1="12" y1="12" x2="12" y2="22"/>
              <line x1="18" y1="12" x2="18" y2="22"/>
            </svg>
          </div>

          <h1>Virement bancaire</h1>

          <p class="transfer-sub">
            Le paiement par virement bancaire sera disponible prochainement.<br>
            Délai de traitement : <strong>2-3 jours ouvrés</strong>.
          </p>

          <div class="transfer-amount" *ngIf="state">
            Montant : <strong>{{ state.amount | number:'1.0-0' }} FCFA</strong>
          </div>

          <!--
          Coordonnées bancaires (à configurer dans payment_provider_config.config_json) :
          IBAN : GA...
          BIC  : BGFIGABB
          Bénéficiaire : QTZ-App SARL
          Référence obligatoire : {{ state?.orderReference }}
          -->

          <div class="info-notice" role="note">
            Les coordonnées bancaires seront affichées ici une fois ce mode activé.
          </div>

          <a routerLink="/checkout" class="btn-back">← Changer de méthode</a>
        </div>
      </div>

      <footer class="pay-footer">
        <span>© 2025 QTZ-App</span>
        <span class="footer-sep"></span>
        <span>Paiement sécurisé</span>
      </footer>

    </section>
  `,
  styles: [`
    :host { display: block; }

    .pay-page {
      min-height: 100vh;
      background: #FAFAFA;
      font-family: 'Inter', sans-serif;
      display: flex;
      flex-direction: column;
    }

    .pay-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 2rem;
      height: 60px;
      background: #FFFFFF;
      box-shadow: 0 1px 6px rgba(0,0,0,0.08);
    }

    .brand { display: flex; align-items: center; gap: 10px; }
    .brand-mark { width: 24px; height: 24px; background: #00653C; }
    .brand-name { font-weight: 700; font-size: 1rem; color: #1A1A1A; }
    .header-secure { display: flex; align-items: center; gap: 6px; font-size: 0.75rem; color: #555; }

    .pay-body {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 3rem 1.5rem;
    }

    .transfer-card {
      background: #FFFFFF;
      border: 1px solid #E0E0E0;
      padding: 3rem 2.5rem;
      max-width: 460px;
      width: 100%;
      text-align: center;
    }

    .transfer-icon {
      width: 72px;
      height: 72px;
      background: #F5F5F5;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: 0 auto 1.5rem;
      color: #555555;
    }

    h1 {
      font-size: 1.3rem;
      font-weight: 700;
      color: #1A1A1A;
      margin-bottom: 0.75rem;
    }

    .transfer-sub {
      font-size: 0.88rem;
      color: #555555;
      line-height: 1.7;
      margin-bottom: 1.5rem;
    }

    .transfer-amount {
      background: #CEFFEB;
      border: 1px solid #E0E0E0;
      padding: 0.75rem 1.5rem;
      font-size: 0.88rem;
      color: #1A1A1A;
      margin-bottom: 1.5rem;

      strong { color: #00653C; font-size: 1.1rem; }
    }

    .info-notice {
      background: #FFF8E1;
      border-left: 3px solid #FFC900;
      padding: 0.75rem 1rem;
      font-size: 0.8rem;
      color: #555555;
      text-align: left;
      margin-bottom: 2rem;
    }

    .btn-back {
      display: inline-block;
      padding: 0.85rem 2rem;
      border: 2px solid #00653C;
      color: #00653C;
      text-decoration: none;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      transition: background 0.15s, color 0.15s;

      &:hover { background: #00653C; color: #fff; }
    }

    .pay-footer {
      background: #FFFFFF;
      border-top: 1px solid #E0E0E0;
      padding: 1.25rem 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 1.5rem;
      font-size: 0.72rem;
      color: #555555;
    }

    .footer-sep { width: 1px; height: 12px; background: #E0E0E0; }
  `]
})
export class PaymentBankTransferComponent implements OnInit {
  state: CheckoutState | null = null;

  constructor(
    private checkoutState: CheckoutStateService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.state = this.checkoutState.get();
    if (!this.state) {
      this.router.navigate(['/catalogue']);
    }
  }
}
