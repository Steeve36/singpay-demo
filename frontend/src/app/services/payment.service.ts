import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly API = '/api/payment';

  constructor(private http: HttpClient) {}

  initierUssd(req: UssdPaymentRequest): Observable<UssdInitResponse> {
    return this.http.post<UssdInitResponse>(`${this.API}/ussd`, req);
  }

  getOrderStatus(reference: string): Observable<OrderStatus> {
    return this.http.get<OrderStatus>(`${this.API}/order/${reference}`);
  }

  createPaymentLink(req: CreatePaymentRequest): Observable<ExtLinkResponse> {
    return this.http.post<ExtLinkResponse>(`${this.API}/create-link`, req);
  }
}
