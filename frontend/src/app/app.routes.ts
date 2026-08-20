import { Routes } from '@angular/router';
import { LandingComponent }                from './landing/landing.component';
import { CatalogueComponent }              from './catalogue/catalogue.component';
import { CheckoutComponent }               from './checkout/checkout.component';
import { PaymentSuccessComponent }         from './payment-success/payment-success.component';
import { PaymentErrorComponent }           from './payment-error/payment-error.component';
import { UssdCheckoutComponent }           from './ussd-checkout/ussd-checkout.component';
import { PaymentMethodLandingComponent }   from './payment-method-landing/payment-method-landing.component';
import { PaymentMobilemoneyComponent }     from './payment/mobile-money/payment-mobile-money.component';
import { PaymentCardComponent }            from './payment/card/payment-card.component';
import { PaymentCardIntegratedComponent }  from './payment/card-integrated/payment-card-integrated.component';
import { PaymentBankTransferComponent }    from './payment/bank-transfer/payment-bank-transfer.component';
import { LoginComponent }                  from './auth/login/login.component';
import { RegisterComponent }               from './auth/register/register.component';
import { authGuard }                       from './guards/auth.guard';

export const routes: Routes = [
  // ── Routes publiques ──────────────────────────────────────────────────────
  { path: '',        component: LandingComponent },
  { path: 'login',   component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  // ── Routes protégées (JWT requis) ─────────────────────────────────────────
  { path: 'catalogue',        component: CatalogueComponent,            canActivate: [authGuard] },
  { path: 'choisir-methode',  component: PaymentMethodLandingComponent, canActivate: [authGuard] },
  { path: 'checkout',         component: CheckoutComponent,             canActivate: [authGuard] },
  { path: 'ussd-checkout',    component: UssdCheckoutComponent,         canActivate: [authGuard] },
  { path: 'paiement/succes',  component: PaymentSuccessComponent,       canActivate: [authGuard] },
  { path: 'paiement/echec',   component: PaymentErrorComponent,         canActivate: [authGuard] },

  { path: 'payment/mobile-money',    component: PaymentMobilemoneyComponent,    canActivate: [authGuard] },
  { path: 'payment/card',            component: PaymentCardComponent,           canActivate: [authGuard] },
  { path: 'payment/card-integrated', component: PaymentCardIntegratedComponent, canActivate: [authGuard] },
  { path: 'payment/bank-transfer',   component: PaymentBankTransferComponent,   canActivate: [authGuard] },
];
