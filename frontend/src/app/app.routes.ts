// app.routes.ts
import { Routes } from '@angular/router';
import { CheckoutComponent }       from './checkout/checkout.component';
import { PaymentSuccessComponent } from './payment-success/payment-success.component';
import { PaymentErrorComponent }   from './payment-error/payment-error.component';

export const routes: Routes = [
  { path: '',          redirectTo: 'checkout', pathMatch: 'full' },
  { path: 'checkout',  component: CheckoutComponent },
  { path: 'paiement/succes', component: PaymentSuccessComponent },
  { path: 'paiement/echec',  component: PaymentErrorComponent },
];
