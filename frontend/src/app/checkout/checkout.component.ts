import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { CheckoutStateService } from '../services/checkout-state.service';

export interface CartItem {
  name: string;
  price: number;
  qty: number;
}

const METHOD_ROUTES: Record<string, string> = {
  'mobile-money':  '/payment/mobile-money',
  'cd':            '/payment/card',
  'cd-integrated': '/payment/card-integrated',
  'virement':      '/payment/bank-transfer',
  'paypal':        '/payment/card',
  'autre':         '/payment/card',
};

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {

  cart: CartItem[] = [];
  customerName  = '';
  customerEmail = '';
  errorMsg      = '';
  reference = `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;
  selectedMethod = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private checkoutState: CheckoutStateService
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const name   = params.get('product');
      const amount = Number(params.get('amount'));
      const method = params.get('method');

      if (method) this.selectedMethod = method;

      if (name && amount) {
        this.cart = [{ name, price: amount, qty: 1 }];
      } else {
        this.cart = [{ name: 'Paiement test', price: 100, qty: 1 }];
      }
    });
  }

  get total(): number {
    return this.cart.reduce((sum, item) => sum + item.price * item.qty, 0);
  }

  get totalFormatted(): string {
    return this.total.toLocaleString('fr-FR') + ' FCFA';
  }

  get isFormValid(): boolean {
    return this.customerName.trim().length > 0 && this.customerEmail.includes('@');
  }

  get productName(): string {
    return this.cart[0]?.name ?? 'Commande';
  }

  get btnLabel(): string {
    return this.selectedMethod ? 'Procéder au paiement' : 'Choisir une méthode';
  }

  continuer(): void {
    if (!this.isFormValid) return;

    const route = METHOD_ROUTES[this.selectedMethod];
    if (route) {
      this.checkoutState.set({
        amount:         this.total,
        orderReference: this.reference,
        customerName:   this.customerName,
        customerEmail:  this.customerEmail,
        productName:    this.productName
      });
      this.router.navigate([route]);
    } else {
      // Aucune méthode sélectionnée → retour au sélecteur avec les params produit
      this.router.navigate(['/choisir-methode'], {
        queryParams: { product: this.productName, amount: this.total }
      });
    }
  }
}
