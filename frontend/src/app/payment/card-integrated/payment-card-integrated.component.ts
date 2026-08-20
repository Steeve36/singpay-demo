import { Component, OnInit, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { v4 as uuidv4 } from 'uuid';
import { firstValueFrom } from 'rxjs';
import { loadStripe, Stripe, StripeElements, Appearance } from '@stripe/stripe-js';
import { PaymentService } from '../../services/payment.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-payment-card-integrated',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payment-card-integrated.component.html',
  styleUrls: ['./payment-card-integrated.component.scss']
})
export class PaymentCardIntegratedComponent implements OnInit, AfterViewInit {

  // ── Données de la commande ────────────────────────────────────────────────────
  productName    = '';
  amount         = 0;
  orderReference = '';

  // ── Champs de facturation ─────────────────────────────────────────────────────
  billingName  = '';
  billingEmail = '';
  billingNameTouched  = false;
  billingEmailTouched = false;

  activeTab: 'integrated' | 'hosted' = 'integrated';

  // ── Checkout intégré — Stripe Elements (mode différé) ────────────────────────
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;

  loadingStripe = true;   // chargement de Stripe.js + montage du Payment Element
  payingIntegrated = false;
  stripeLoadError  = '';
  paymentErrorMsg  = '';
  transactionIdIntegrated: number | null = null;

  // Clé régénérée à chaque tentative de paiement
  private idemIntegrated = uuidv4();

  @ViewChild('stripePaymentElement') private stripeElementRef!: ElementRef;

  // ── Checkout hébergé (Stripe Checkout Session) ────────────────────────────────
  private static readonly IDEM_HOSTED = 'card_hosted_idem_key';
  private static readonly REF_KEY     = 'card_integrated_ref';

  loadingHosted  = false;
  hostedErrorMsg = '';
  private idemHosted = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private paymentService: PaymentService
  ) {}

  get totalFormatted(): string {
    return this.amount.toLocaleString('fr-FR') + ' FCFA';
  }

  get nameInvalid(): boolean {
    return this.billingNameTouched && !this.billingName.trim();
  }

  get emailInvalid(): boolean {
    return this.billingEmailTouched && !this.billingEmail.trim().includes('@');
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.productName = params.get('product') ?? '';
      this.amount      = Number(params.get('amount') ?? '0');
    });

    if (!this.amount || this.amount < 1) {
      this.router.navigate(['/catalogue']);
      return;
    }

    // Référence commande stable en sessionStorage
    const storedRef = sessionStorage.getItem(PaymentCardIntegratedComponent.REF_KEY);
    this.orderReference = storedRef
      ?? `CMD-${Date.now()}-${uuidv4().slice(0, 8).toUpperCase()}`;
    if (!storedRef) {
      sessionStorage.setItem(PaymentCardIntegratedComponent.REF_KEY, this.orderReference);
    }

    // Idempotence onglet hébergé
    const storedH = sessionStorage.getItem(PaymentCardIntegratedComponent.IDEM_HOSTED);
    this.idemHosted = storedH ?? uuidv4();
    if (!storedH) {
      sessionStorage.setItem(PaymentCardIntegratedComponent.IDEM_HOSTED, this.idemHosted);
    }
  }

  ngAfterViewInit(): void {
    if (!this.amount || this.amount < 1) return;
    this.initStripeElements();
  }

  selectTab(tab: 'integrated' | 'hosted'): void {
    this.activeTab = tab;
  }

  // ── Checkout intégré ──────────────────────────────────────────────────────────

  /** Monte le Payment Element en mode différé (sans PI serveur, avant le clic "Payer"). */
  private async initStripeElements(): Promise<void> {
    const key = environment.stripePublishableKey;
    if (!key || key.includes('REPLACE')) {
      this.stripeLoadError = 'Clé Stripe publique non configurée (stripePublishableKey dans environment.ts).';
      this.loadingStripe = false;
      return;
    }

    try {
      this.stripe = await loadStripe(key);
    } catch {
      this.stripeLoadError = 'Impossible de charger Stripe.js.';
      this.loadingStripe = false;
      return;
    }

    if (!this.stripe) {
      this.stripeLoadError = 'Stripe.js non disponible.';
      this.loadingStripe = false;
      return;
    }

    // Apparence calquée sur le design system de l'application
    const appearance: Appearance = {
      disableAnimations: true,
      variables: {
        fontFamily:           '"Inter", system-ui, sans-serif',
        fontSizeBase:         '14px',
        fontWeightMedium:     '500',
        fontWeightBold:       '600',
        colorPrimary:         '#00653C',
        colorBackground:      '#FFFFFF',
        colorText:            '#1A1A1A',
        colorDanger:          '#DC2626',
        colorTextSecondary:   '#555555',
        colorTextPlaceholder: '#AAAAAA',
        iconColor:            '#555555',
        borderRadius:         '0px',
        spacingUnit:          '4px',
        gridRowSpacing:       '12px',
        gridColumnSpacing:    '12px',
      },
      rules: {
        // ── Inputs ────────────────────────────────────────────────────────────
        '.Input': {
          border:          '1px solid #E0E0E0',
          padding:         '10px 12px',
          fontSize:        '14px',
          color:           '#1A1A1A',
          backgroundColor: '#FFFFFF',
          boxShadow:       'none',
          transition:      'border-color 0.15s, box-shadow 0.15s',
        },
        '.Input:hover': {
          borderColor: '#AAAAAA',
        },
        '.Input:focus': {
          borderColor: '#00653C',
          boxShadow:   '0 0 0 3px rgba(0, 101, 60, 0.1)',
          outline:     'none',
        },
        '.Input--invalid': {
          borderColor: '#DC2626',
          boxShadow:   'none',
        },
        '.Input--invalid:focus': {
          boxShadow: '0 0 0 3px rgba(220, 38, 38, 0.1)',
        },

        // ── Labels ────────────────────────────────────────────────────────────
        '.Label': {
          fontSize:      '10px',
          fontWeight:    '700',
          letterSpacing: '0.1em',
          textTransform: 'uppercase',
          color:         '#555555',
          marginBottom:  '5px',
        },

        // ── Messages d'erreur ─────────────────────────────────────────────────
        '.Error': {
          fontSize:   '11.5px',
          color:      '#DC2626',
          marginTop:  '4px',
          fontWeight: '400',
        },

        // ── Onglets de méthode de paiement ────────────────────────────────────
        '.Tab': {
          border:          '1px solid #E0E0E0',
          borderRadius:    '0px',
          padding:         '10px 16px',
          backgroundColor: '#FAFAFA',
          color:           '#555555',
          fontSize:        '12.5px',
          fontWeight:      '500',
          boxShadow:       'none',
          transition:      'color 0.15s, background 0.15s, border-color 0.15s',
        },
        '.Tab:hover': {
          color:           '#1A1A1A',
          backgroundColor: '#F0F0F0',
          borderColor:     '#AAAAAA',
        },
        '.Tab--selected': {
          backgroundColor: '#CEFFEB',
          color:           '#00653C',
          borderColor:     '#00653C',
          fontWeight:      '600',
          boxShadow:       'none',
        },
        '.Tab--selected:focus': {
          boxShadow: '0 0 0 3px rgba(0, 101, 60, 0.15)',
          outline:   'none',
        },
        '.Tab:focus': {
          boxShadow: '0 0 0 3px rgba(0, 101, 60, 0.1)',
          outline:   'none',
        },
        '.TabIcon--selected': {
          fill:  '#00653C',
          color: '#00653C',
        },
        '.TabLabel--selected': {
          color: '#00653C',
        },

        // ── Cases à cocher (mandat, enregistrement carte) ─────────────────────
        '.CheckboxInput': {
          border:          '1px solid #E0E0E0',
          borderRadius:    '0px',
          backgroundColor: '#FFFFFF',
        },
        '.CheckboxInput--checked': {
          backgroundColor: '#00653C',
          borderColor:     '#00653C',
        },

        // ── Liste déroulante (pays, etc.) ─────────────────────────────────────
        '.DropdownItem--highlight': {
          backgroundColor: '#CEFFEB',
          color:           '#00653C',
        },

        // ── Bloc conteneur (ex : accord de paiement) ──────────────────────────
        '.Block': {
          border:          '1px solid #E0E0E0',
          borderRadius:    '0px',
          backgroundColor: '#FAFAFA',
        },

        // ── Textes secondaires (redirect, mentions légales) ───────────────────
        '.RedirectText': {
          color:    '#555555',
          fontSize: '12px',
        },
        '.TermsText': {
          fontSize: '11px',
          color:    '#555555',
        },
        '.TermsLink': {
          color:          '#00653C',
          textDecoration: 'underline',
        },
        '.TermsLink:hover': {
          color: '#004d2d',
        },
      }
    };

    // Mode différé : Stripe.js monte le formulaire sans créer de PaymentIntent
    // (le PI est créé uniquement au clic "Payer", avec les vrais nom/email)
    this.elements = this.stripe.elements({
      mode:     'payment',
      amount:   Math.round(this.amount),
      currency: 'xaf',
      locale:   'fr',
      fonts:    [{ cssSrc: 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap' }],
      appearance,
    });

    const paymentElement = this.elements.create('payment', {
      fields: {
        billingDetails: { name: 'never', email: 'never' }
      }
    });
    paymentElement.mount(this.stripeElementRef.nativeElement);
    paymentElement.on('ready',     () => { this.loadingStripe = false; });
    paymentElement.on('loaderror', () => {
      this.stripeLoadError = 'Erreur lors du chargement du formulaire Stripe.';
      this.loadingStripe = false;
    });
  }

  async payerIntegre(): Promise<void> {
    // 1 — Validation champs de facturation
    this.billingNameTouched  = true;
    this.billingEmailTouched = true;
    if (this.nameInvalid || this.emailInvalid) return;

    if (!this.stripe || !this.elements || this.payingIntegrated) return;

    this.payingIntegrated = true;
    this.paymentErrorMsg  = '';

    // 2 — Validation du formulaire carte Stripe (avant tout appel serveur)
    const { error: submitError } = await this.elements.submit();
    if (submitError) {
      this.paymentErrorMsg  = submitError.message ?? 'Veuillez vérifier vos informations de carte.';
      this.payingIntegrated = false;
      return;
    }

    // 3 — Création du PaymentIntent avec les vraies coordonnées de facturation
    let clientSecret: string;
    try {
      const res = await firstValueFrom(
        this.paymentService.createPaymentIntent({
          amount:         Math.round(this.amount),
          currency:       'XAF',
          orderReference: this.orderReference,
          idempotencyKey: this.idemIntegrated,
          customerName:   this.billingName.trim(),
          customerEmail:  this.billingEmail.trim(),
        })
      );
      clientSecret = res.clientSecret;
      this.transactionIdIntegrated = res.transactionId;
    } catch (err: any) {
      this.paymentErrorMsg  = err?.error?.message ?? 'Erreur lors de la création du paiement.';
      this.payingIntegrated = false;
      // Régénérer la clé d'idempotence pour permettre une nouvelle tentative
      this.idemIntegrated = uuidv4();
      return;
    }

    // 4 — Confirmation du paiement (3DS ou en-page)
    sessionStorage.setItem('checkout_state_card', JSON.stringify({
      amount:        this.amount,
      customerName:  this.billingName.trim(),
      customerEmail: this.billingEmail.trim(),
    }));

    const returnUrl = `${window.location.origin}/paiement/succes`
      + `?txnId=${this.transactionIdIntegrated}`
      + `&reference=${encodeURIComponent(this.orderReference)}`;

    const { error, paymentIntent } = await this.stripe.confirmPayment({
      elements:      this.elements,
      clientSecret,
      confirmParams: {
        return_url: returnUrl,
        payment_method_data: {
          billing_details: {
            name:  this.billingName.trim(),
            email: this.billingEmail.trim(),
          }
        }
      },
      redirect: 'if_required'
    });

    if (error) {
      this.paymentErrorMsg  = error.message ?? 'Le paiement a échoué. Veuillez réessayer.';
      this.payingIntegrated = false;
      this.idemIntegrated   = uuidv4(); // nouvelle tentative = nouveau PI
    } else if (paymentIntent?.status === 'succeeded') {
      this.router.navigate(['/paiement/succes'], {
        queryParams: {
          txnId:     this.transactionIdIntegrated,
          reference: this.orderReference
        }
      });
    } else {
      this.payingIntegrated = false;
    }
  }

  // ── Checkout hébergé ──────────────────────────────────────────────────────────

  payerHote(): void {
    this.billingNameTouched  = true;
    this.billingEmailTouched = true;
    if (this.nameInvalid || this.emailInvalid) return;
    if (this.loadingHosted) return;

    this.loadingHosted  = true;
    this.hostedErrorMsg = '';

    sessionStorage.setItem('checkout_state_card', JSON.stringify({
      amount:        this.amount,
      customerName:  this.billingName.trim(),
      customerEmail: this.billingEmail.trim(),
    }));

    this.paymentService.initiatePayment({
      method:         'CB',
      amount:         Math.round(this.amount),
      currency:       'XAF',
      orderReference: this.orderReference,
      idempotencyKey: this.idemHosted,
      customerName:   this.billingName.trim(),
      customerEmail:  this.billingEmail.trim(),
    }).subscribe({
      next: (response) => {
        if (response.paymentUrl) {
          window.location.href = response.paymentUrl;
        } else {
          this.loadingHosted  = false;
          this.hostedErrorMsg = 'Aucune URL de paiement reçue.';
        }
      },
      error: (err) => {
        this.loadingHosted  = false;
        this.hostedErrorMsg = err?.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.';
      }
    });
  }

  // ── Navigation ────────────────────────────────────────────────────────────────

  changerMethode(): void {
    sessionStorage.removeItem(PaymentCardIntegratedComponent.IDEM_HOSTED);
    sessionStorage.removeItem(PaymentCardIntegratedComponent.REF_KEY);
    this.router.navigate(['/choisir-methode'], {
      queryParams: { product: this.productName, amount: this.amount }
    });
  }
}
