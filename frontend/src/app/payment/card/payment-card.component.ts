import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { CheckoutState, CheckoutStateService } from '../../services/checkout-state.service';
import { PaymentService } from '../../services/payment.service';

@Component({
  selector: 'app-payment-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-card.component.html',
  styleUrls: ['./payment-card.component.scss']
})
export class PaymentCardComponent implements OnInit {

  state: CheckoutState | null = null;
  loading = false;
  errorMsg = '';

  private idempotencyKey = '';

  private static readonly IDEM_KEY = 'card_idempotency_key';

  constructor(
    private checkoutState: CheckoutStateService,
    private paymentService: PaymentService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.state = this.checkoutState.get();
    if (!this.state) {
      this.router.navigate(['/catalogue']);
      return;
    }
    // Reuse existing key across page reloads to prevent duplicate charges
    const stored = sessionStorage.getItem(PaymentCardComponent.IDEM_KEY);
    this.idempotencyKey = stored ?? uuidv4();
    if (!stored) {
      sessionStorage.setItem(PaymentCardComponent.IDEM_KEY, this.idempotencyKey);
    }
  }

  get totalFormatted(): string {
    return (this.state?.amount ?? 0).toLocaleString('fr-FR') + ' FCFA';
  }

  payer(): void {
    if (!this.state || this.loading) return;
    this.loading = true;
    this.errorMsg = '';

    // Sauvegarde avant redirect externe — Stripe vide la mémoire Angular au retour
    sessionStorage.setItem('checkout_state_card', JSON.stringify(this.state));

    this.paymentService.initiatePayment({
      method: 'CB',
      amount: Math.round(this.state.amount),
      currency: 'XAF',
      orderReference: this.state.orderReference,
      idempotencyKey: this.idempotencyKey,
      customerName: this.state.customerName,
      customerEmail: this.state.customerEmail
    }).subscribe({
      next: (response) => {
        if (response.paymentUrl) {
          window.location.href = response.paymentUrl;
        } else {
          this.loading = false;
          this.errorMsg = 'Aucune URL de paiement reçue.';
        }
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err?.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.';
      }
    });
  }

  changerMethode(): void {
    sessionStorage.removeItem(PaymentCardComponent.IDEM_KEY);
    this.router.navigate(['/choisir-methode'], {
      queryParams: {
        product: this.state?.productName ?? '',
        amount:  this.state?.amount ?? 0
      }
    });
  }
}
