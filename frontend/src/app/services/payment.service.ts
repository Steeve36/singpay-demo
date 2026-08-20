import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// ── Multi-gateway API (nouveaux endpoints) ───────────────────────────────────

export type PaymentMethod = 'CB' | 'MOBILE_MONEY' | 'BANK_TRANSFER';
export type TxnStatus     = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

export interface PaymentInitRequest {
  method: PaymentMethod;
  amount: number;
  currency: string;
  orderReference: string;
  idempotencyKey: string;
  customerName: string;
  customerEmail: string;
  operateur?: string;
  phone?: string;
  redirectSuccess?: string;
  redirectError?: string;
  subtype?: 'USSD' | 'EXT_LINK';
}

export interface PaymentInitResponse {
  transactionId: number;
  orderReference: string;
  method: PaymentMethod;
  provider: string;
  status: TxnStatus;
  paymentUrl?: string;
  providerRef?: string;
}

export interface PaymentStatusResponse {
  id: number;
  orderReference: string;
  method: PaymentMethod;
  provider: string;
  status: TxnStatus;
  amount: number;
  currency: string;
  providerRef?: string;
  failureReason?: string;
  customerName?: string;
  customerEmail?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProviderConfig {
  provider: string;
  method: PaymentMethod;
  active: boolean;
  priority: number;
  configJson?: string;
}

export interface UssdPaymentRequest {
  amount: number;
  reference: string;
  phone: string;
  operateur: 'AIRTEL' | 'MOOV' | 'MAVIANCE';
  customerName: string;
  customerEmail: string;
}

export interface UssdInitResponse {
  reference: string;
  transactionId: string;
}

export interface OrderStatus {
  status: string;          // PENDING | PAID | FAILED_* | FRAUD_SUSPECTED
  amount: number;
  reference: string;
  operateur: string;
  clientMsisdn: string;    // déjà masqué par le backend
  airtelMoneyId: string;
  customerName: string;
  customerEmail: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePaymentRequest {
  amount: number;
  reference: string;
  customerName: string;
  customerEmail: string;
}

export interface ExtLinkResponse {
  link: string;
  exp: string;
}

export interface CreateIntentRequest {
  amount: number;
  currency: string;
  orderReference: string;
  idempotencyKey: string;
  customerName: string;
  customerEmail: string;
}

export interface CreateIntentResponse {
  clientSecret: string;
  transactionId: number;
  orderReference: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly API     = '/api/payment';
  private readonly GW_API  = '/api/payments';

  constructor(private http: HttpClient) {}

  // ── Anciens endpoints (rétrocompatibilité) ────────────────────────────────

  initierUssd(req: UssdPaymentRequest): Observable<UssdInitResponse> {
    return this.http.post<UssdInitResponse>(`${this.API}/ussd`, req);
  }

  getOrderStatus(reference: string): Observable<OrderStatus> {
    return this.http.get<OrderStatus>(`${this.API}/order/${reference}`);
  }

  createPaymentLink(req: CreatePaymentRequest): Observable<ExtLinkResponse> {
    return this.http.post<ExtLinkResponse>(`${this.API}/create-link`, req);
  }

  // ── Nouveaux endpoints multi-passerelles ──────────────────────────────────

  initiatePayment(req: PaymentInitRequest): Observable<PaymentInitResponse> {
    return this.http.post<PaymentInitResponse>(`${this.GW_API}/initiate`, req);
  }

  getTransactionStatus(transactionId: number): Observable<PaymentStatusResponse> {
    return this.http.get<PaymentStatusResponse>(`${this.GW_API}/${transactionId}/status`);
  }

  /** Vérifie en live le statut auprès du provider (ex: Stripe) et met à jour la DB. */
  verifyPayment(transactionId: number): Observable<PaymentStatusResponse> {
    return this.http.get<PaymentStatusResponse>(`${this.GW_API}/${transactionId}/verify`);
  }

  /** Crée un Stripe PaymentIntent pour le checkout intégré (Stripe Elements). */
  createPaymentIntent(req: CreateIntentRequest): Observable<CreateIntentResponse> {
    return this.http.post<CreateIntentResponse>(`${this.GW_API}/create-intent`, req);
  }
}
