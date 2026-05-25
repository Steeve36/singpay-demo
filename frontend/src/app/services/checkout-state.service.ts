import { Injectable } from '@angular/core';

export interface CheckoutState {
  amount: number;
  orderReference: string;
  customerName: string;
  customerEmail: string;
  productName: string;
}

@Injectable({ providedIn: 'root' })
export class CheckoutStateService {
  private state: CheckoutState | null = null;

  set(data: CheckoutState): void {
    this.state = data;
  }

  get(): CheckoutState | null {
    return this.state;
  }

  clear(): void {
    this.state = null;
  }
}
