import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CheckoutStateService } from '../../services/checkout-state.service';

type MethodId = 'mobile-money' | 'card' | 'bank-transfer';

interface PaymentMethodCard {
  id: MethodId;
  label: string;
  sublabel: string;
  available: boolean;
  ariaLabel: string;
}

@Component({
  selector: 'app-payment-method-selector',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-method-selector.component.html',
  styleUrls: ['./payment-method-selector.component.scss']
})
export class PaymentMethodSelectorComponent implements OnInit {
  @Input() amount       = 0;
  @Input() reference    = '';
  @Input() customerName  = '';
  @Input() customerEmail = '';
  @Input() productName   = '';

  selected: MethodId | null = null;
  loading = false;

  methods: PaymentMethodCard[] = [
    {
      id: 'mobile-money',
      label: 'Payer via Mobile Money',
      sublabel: 'Airtel Money, Moov Money',
      available: true,
      ariaLabel: 'Sélectionner Mobile Money comme mode de paiement'
    },
    {
      id: 'card',
      label: 'Payer par carte bancaire',
      sublabel: 'Visa, Mastercard — Paiement sécurisé',
      available: false,
      ariaLabel: 'Sélectionner carte bancaire comme mode de paiement (bientôt disponible)'
    },
    {
      id: 'bank-transfer',
      label: 'Payer par virement bancaire',
      sublabel: 'Traitement sous 2-3 jours ouvrés',
      available: false,
      ariaLabel: 'Sélectionner virement bancaire comme mode de paiement (bientôt disponible)'
    }
  ];

  constructor(
    private router: Router,
    private checkoutState: CheckoutStateService
  ) {}

  ngOnInit(): void {
    this.checkoutState.set({
      amount:         this.amount,
      orderReference: this.reference,
      customerName:   this.customerName,
      customerEmail:  this.customerEmail,
      productName:    this.productName
    });
  }

  select(method: PaymentMethodCard): void {
    if (!method.available || this.loading) return;
    this.selected = method.id;
  }

  confirm(): void {
    if (!this.selected || this.loading) return;
    this.loading = true;
    this.router.navigate(['/payment', this.selected]);
  }

  trackById(_: number, m: PaymentMethodCard): string {
    return m.id;
  }
}
