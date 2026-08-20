import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { CheckoutStateService } from '../services/checkout-state.service';

type MethodId = 'cd' | 'cd-integrated' | 'mobile-money' | 'virement' | 'paypal' | 'autre';

interface MethodCard {
  id: MethodId;
  label: string;
  sublabel: string;
  available: boolean;
  targetMethod: string;
}

@Component({
  selector: 'app-payment-method-landing',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-method-landing.component.html',
  styleUrls: ['./payment-method-landing.component.scss']
})
export class PaymentMethodLandingComponent implements OnInit {

  productName = '';
  amount = 0;

  methods: MethodCard[] = [
    {
      id: 'cd',
      label: 'Carte de Débit',
      sublabel: 'Visa, Mastercard · Checkout Stripe',
      available: true,
      targetMethod: 'cd'
    },
    {
      id: 'cd-integrated',
      label: 'Carte de Débit',
      sublabel: 'Formulaire intégré · Checkout intégré',
      available: true,
      targetMethod: 'cd-integrated'
    },
    {
      id: 'mobile-money',
      label: 'Mobile Money',
      sublabel: 'Airtel Money · Moov Money',
      available: true,
      targetMethod: 'mobile-money'
    },
    {
      id: 'virement',
      label: 'Virement Bancaire',
      sublabel: 'Traitement 2–3 jours ouvrés',
      available: false,
      targetMethod: 'virement'
    },
    {
      id: 'paypal',
      label: 'PayPal',
      sublabel: 'Paiement sécurisé PayPal',
      available: false,
      targetMethod: 'paypal'
    },
    {
      id: 'autre',
      label: 'Autre(s)',
      sublabel: "D'autres méthodes à venir",
      available: false,
      targetMethod: 'autre'
    }
  ];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private checkoutState: CheckoutStateService
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.productName = params.get('product') ?? '';
      this.amount = Number(params.get('amount') ?? '0');
    });
  }

  get amountFormatted(): string {
    return this.amount.toLocaleString('fr-FR') + ' FCFA';
  }

  select(method: MethodCard): void {
    if (!method.available) return;

    if (method.id === 'mobile-money') {
      const reference = `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;
      this.checkoutState.set({
        amount:         this.amount,
        productName:    this.productName,
        orderReference: reference,
        customerName:   '',
        customerEmail:  ''
      });
      this.router.navigate(['/payment/mobile-money']);
      return;
    }

    // Checkout intégré : tout sur une seule page (nom + email + carte)
    if (method.id === 'cd-integrated') {
      this.router.navigate(['/payment/card-integrated'], {
        queryParams: { product: this.productName, amount: this.amount }
      });
      return;
    }

    this.router.navigate(['/checkout'], {
      queryParams: {
        product: this.productName,
        amount:  this.amount,
        method:  method.targetMethod
      }
    });
  }

  retour(): void {
    this.location.back();
  }
}
