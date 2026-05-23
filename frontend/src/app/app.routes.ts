// app.routes.ts
import { Routes } from '@angular/router';
import { CatalogueComponent }      from './catalogue/catalogue.component';
import { CheckoutComponent }       from './checkout/checkout.component';
import { PaymentSuccessComponent } from './payment-success/payment-success.component';
import { PaymentErrorComponent }   from './payment-error/payment-error.component';
import { UssdCheckoutComponent }   from './ussd-checkout/ussd-checkout.component';

export const routes: Routes = [
  { path: '',                 component: CatalogueComponent },
  { path: 'checkout',         component: CheckoutComponent },
  { path: 'ussd-checkout',    component: UssdCheckoutComponent },
  { path: 'paiement/succes',  component: PaymentSuccessComponent },
  { path: 'paiement/echec',   component: PaymentErrorComponent },
];
