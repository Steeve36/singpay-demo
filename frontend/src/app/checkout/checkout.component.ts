import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { UssdCheckoutComponent } from '../ussd-checkout/ussd-checkout.component';
import { OrderStatus } from '../services/payment.service';

export interface CartItem {
  name: string;
  price: number;
  qty: number;
}

export interface CreateLinkRequest {
  amount: number;
  reference: string;
  customerName: string;
  customerEmail: string;
}

export interface CreateLinkResponse {
  link: string;
  exp: string;
  reference: string;
}

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule, UssdCheckoutComponent],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {

  cart: CartItem[] = [];

  // Formulaire
  customerName  = '';
  customerEmail = '';

  // Opérateur sélectionné (affiché avant redirect, pas envoyé à SingPay — c'est la page /ext qui gère)
  selectedOperator = '';

  // États UI
  loading   = false;
  errorMsg  = '';

  // Référence unique pour cette commande
  reference = `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;

  // Mode de paiement : USSD Push ou lien externe SingPay
  paymentMode: 'ussd' | 'ext' = 'ussd';

  // Contrôle la recréation du composant USSD après annulation
  ussdActive = true;

  constructor(
    private http: HttpClient,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const name   = params.get('product');
      const amount = Number(params.get('amount'));
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
    return this.customerName.trim().length > 0
      && this.customerEmail.includes('@')
      && !this.loading;
  }

  /**
   * Crée le lien de paiement SingPay via le backend Spring Boot,
   * puis redirige l'utilisateur vers la page de paiement hébergée.
   */
  pay(): void {
    if (!this.isFormValid || this.loading) return;

    this.loading  = true;
    this.errorMsg = '';

    const payload: CreateLinkRequest = {
      amount:        this.total,
      reference:     this.reference,
      customerName:  this.customerName,
      customerEmail: this.customerEmail,
    };

    this.http.post<CreateLinkResponse>('/api/payment/create-link', payload)
      .subscribe({
        next: (res) => {
          // Redirection vers la page de paiement SingPay
          window.location.href = res.link;
        },
        error: (err) => {
          this.loading  = false;
          this.errorMsg = err?.error?.message
            ?? 'Une erreur est survenue. Veuillez réessayer.';
        }
      });
  }

  onPaymentDone(order: OrderStatus): void {
    this.router.navigate(['/paiement/succes'], {
      queryParams: { reference: order.reference }
    });
  }

  onPaymentCancelled(): void {
    // Régénère la référence et recrée le composant USSD pour une nouvelle tentative
    this.reference = `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;
    this.ussdActive = false;
    setTimeout(() => { this.ussdActive = true; }, 0);
  }
}
