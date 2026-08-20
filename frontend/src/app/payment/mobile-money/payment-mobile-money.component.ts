import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { CheckoutStateService, CheckoutState } from '../../services/checkout-state.service';
import { UssdCheckoutComponent } from '../../ussd-checkout/ussd-checkout.component';
import { OrderStatus } from '../../services/payment.service';

@Component({
  selector: 'app-payment-mobile-money',
  standalone: true,
  imports: [CommonModule, RouterModule, UssdCheckoutComponent],
  templateUrl: './payment-mobile-money.component.html',
  styleUrls: ['./payment-mobile-money.component.scss']
})
export class PaymentMobilemoneyComponent implements OnInit {

  state: CheckoutState | null = null;
  ussdActive = true;
  errorMsg = '';
  currentOperateur: 'AIRTEL' | 'MOOV' | 'MAVIANCE' | '' = '';

  constructor(
    private checkoutState: CheckoutStateService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.state = this.checkoutState.get();
    if (!this.state) {
      // Pas d'état de checkout — retour à la page d'accueil
      this.router.navigate(['/catalogue']);
    }
  }

  get totalFormatted(): string {
    return (this.state?.amount ?? 0).toLocaleString('fr-FR') + ' FCFA';
  }

  onPaymentDone(order: OrderStatus): void {
    this.checkoutState.clear();
    this.router.navigate(['/paiement/succes'], {
      queryParams: { reference: order.reference }
    });
  }

  changerMethode(): void {
    this.router.navigate(['/choisir-methode'], {
      queryParams: {
        product: this.state?.productName ?? '',
        amount:  this.state?.amount ?? 0
      }
    });
  }

  onOperateurChange(op: 'AIRTEL' | 'MOOV' | 'MAVIANCE' | ''): void {
    this.currentOperateur = op;
  }

  onPaymentCancelled(): void {
    if (this.state) {
      // Régénère la référence pour une nouvelle tentative
      const newRef = `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;
      this.checkoutState.set({ ...this.state, orderReference: newRef });
      this.state = this.checkoutState.get();
    }
    this.ussdActive = false;
    setTimeout(() => { this.ussdActive = true; }, 0);
  }

}
