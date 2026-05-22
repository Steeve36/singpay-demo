import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './checkout.component.html',
  styleUrls: ['./checkout.component.scss']
})
export class CheckoutComponent implements OnInit {

  // Données du panier (simulées ou passées via un service)
  cart: CartItem[] = [
    { name: 'Paiement test', price: 1000, qty: 1 },
  ];

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

  constructor(
    private http: HttpClient,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {}

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
    if (!this.isFormValid) return;

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
}
