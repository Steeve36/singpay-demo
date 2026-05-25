import { Routes } from '@angular/router';
import { CatalogueComponent }              from './catalogue/catalogue.component';
import { CheckoutComponent }               from './checkout/checkout.component';
import { PaymentSuccessComponent }         from './payment-success/payment-success.component';
import { PaymentErrorComponent }           from './payment-error/payment-error.component';
import { UssdCheckoutComponent }           from './ussd-checkout/ussd-checkout.component';
import { PaymentMethodLandingComponent }   from './payment-method-landing/payment-method-landing.component';
import { PaymentMobilemoneyComponent }     from './payment/mobile-money/payment-mobile-money.component';
import { PaymentCardComponent }            from './payment/card/payment-card.component';
import { PaymentBankTransferComponent }    from './payment/bank-transfer/payment-bank-transfer.component';

export const routes: Routes = [
  { path: '',                    component: CatalogueComponent },
  { path: 'choisir-methode',     component: PaymentMethodLandingComponent },
  { path: 'checkout',            component: CheckoutComponent },
  { path: 'ussd-checkout',       component: UssdCheckoutComponent },
  { path: 'paiement/succes',     component: PaymentSuccessComponent },
  { path: 'paiement/echec',      component: PaymentErrorComponent },
  { path: 'payment/mobile-money',  component: PaymentMobilemoneyComponent },
  { path: 'payment/card',          component: PaymentCardComponent },
  { path: 'payment/bank-transfer', component: PaymentBankTransferComponent },
];
