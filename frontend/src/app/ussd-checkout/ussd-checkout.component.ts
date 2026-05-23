import { Component, Input, Output, EventEmitter, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, interval, of } from 'rxjs';
import { catchError, filter, startWith, switchMap, take, takeUntil, takeWhile, tap } from 'rxjs/operators';
import { PaymentService, OrderStatus } from '../services/payment.service';

@Component({
  selector: 'app-ussd-checkout',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ussd-checkout.component.html',
  styleUrls: ['./ussd-checkout.component.scss']
})
export class UssdCheckoutComponent implements OnDestroy {

  @Input() amount: number = 0;
  @Input() reference: string = '';
  @Output() done = new EventEmitter<OrderStatus>();
  @Output() cancelled = new EventEmitter<void>();

  step: 'operator' | 'phone' | 'confirm' | 'waiting' |
        'success' | 'failed_balance' | 'failed_pin' |
        'failed_timeout_singpay' | 'timeout_angular' = 'operator';

  operateur: 'AIRTEL' | 'MOOV' | 'MAVIANCE' | '' = '';
  phone = '';
  customerName = '';
  customerEmail = '';
  loading = false;
  countdown = 60;
  completedOrder: OrderStatus | null = null;
  errorMsg = '';
  checkingStatus = false;
  statusCheckResult: 'pending' | 'error' | null = null;

  private stopPolling$ = new Subject<void>();

  constructor(private paymentService: PaymentService) {}

  get amountFormatted(): string {
    return this.amount.toLocaleString('fr-FR') + ' FCFA';
  }

  get phonePlaceholder(): string {
    switch (this.operateur) {
      case 'AIRTEL':   return '074 XX XX XX';
      case 'MOOV':     return '062 XX XX XX';
      case 'MAVIANCE': return '07X XX XX XX';
      default:         return '0XX XX XX XX';
    }
  }

  get isStep2Valid(): boolean {
    return this.customerName.trim().length > 0
      && this.customerEmail.includes('@')
      && this.isPhoneValid;
  }

  get isPhoneValid(): boolean {
    const p = this.phone.replace(/[^0-9]/g, '');
    switch (this.operateur) {
      case 'AIRTEL':   return /^(074|076|077)\d{6}$/.test(p);
      case 'MOOV':     return /^(062|065|066)\d{6}$/.test(p);
      case 'MAVIANCE': return /^0[67]\d{7}$/.test(p);
      default:         return false;
    }
  }

  get maskedPhone(): string {
    const p = this.phone.replace(/[^0-9]/g, '');
    if (p.length < 6) return '***';
    return p.substring(0, 3) + '****' + p.substring(p.length - 2);
  }

  get operateurLabel(): string {
    switch (this.operateur) {
      case 'AIRTEL':   return 'Airtel Money';
      case 'MOOV':     return 'Moov Money';
      case 'MAVIANCE': return 'Maviance';
      default:         return '';
    }
  }

  get operateurColor(): string {
    switch (this.operateur) {
      case 'AIRTEL':   return '#FF6600';
      case 'MOOV':     return '#0066CC';
      case 'MAVIANCE': return '#1D9E75';
      default:         return '';
    }
  }

  onPhoneInput(): void {
    this.phone = this.phone.replace(/[^0-9]/g, '');
  }

  goToPhone(): void {
    if (!this.operateur) return;
    this.phone = '';
    this.customerName = '';
    this.customerEmail = '';
    this.errorMsg = '';
    this.step = 'phone';
  }

  goToConfirm(): void {
    if (!this.isStep2Valid) return;
    this.step = 'confirm';
  }

  confirmer(): void {
    if (this.loading) return;
    this.loading = true;
    this.errorMsg = '';

    this.paymentService.initierUssd({
      amount: this.amount,
      reference: this.reference,
      phone: this.phone,
      operateur: this.operateur as 'AIRTEL' | 'MOOV' | 'MAVIANCE',
      customerName: this.customerName,
      customerEmail: this.customerEmail
    }).subscribe({
      next: (res) => {
        this.step = 'waiting';
        this.startPolling(res.reference);
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err?.error?.message ?? 'Erreur lors de l\'initiation du paiement.';
      }
    });
  }

  annuler(): void {
    this.stopPolling$.next();
    this.loading = false;
    this.statusCheckResult = null;
    this.checkingStatus = false;
    this.step = 'operator';
    this.cancelled.emit();
  }

  recommencer(): void {
    this.stopPolling$.next();
    this.loading = false;
    this.errorMsg = '';
    this.statusCheckResult = null;
    this.checkingStatus = false;
    this.step = 'operator';
    this.cancelled.emit();
  }

  verifierStatut(): void {
    this.checkingStatus = true;
    this.statusCheckResult = null;

    this.paymentService.getOrderStatus(this.reference).subscribe({
      next: (order) => {
        this.checkingStatus = false;
        if (order.status === 'PENDING') {
          this.statusCheckResult = 'pending';
        } else {
          this.handleResult(order);
        }
      },
      error: () => {
        this.checkingStatus = false;
        this.statusCheckResult = 'error';
      }
    });
  }

  private startPolling(reference: string): void {
    this.countdown = 60;

    // Countdown visuel — décrément chaque seconde
    const countdown$ = interval(1000).pipe(
      take(60),
      tap(() => this.countdown--),
      takeUntil(this.stopPolling$)
    );

    // Polling statut — interroge le backend toutes les 5 secondes
    const poll$ = interval(5000).pipe(
      startWith(0),
      switchMap(() =>
        this.paymentService.getOrderStatus(reference).pipe(
          catchError(() => of(null))
        )
      ),
      filter((res): res is OrderStatus => res !== null),
      takeWhile(res => res.status === 'PENDING', true),
      take(13),
      takeUntil(this.stopPolling$)
    );

    countdown$.subscribe();

    poll$.subscribe({
      next: (res) => {
        if (res.status !== 'PENDING') {
          this.handleResult(res);
        }
      },
      complete: () => {
        if (this.step === 'waiting') {
          this.step = 'timeout_angular';
        }
      }
    });
  }

  private handleResult(order: OrderStatus): void {
    this.completedOrder = order;
    switch (order.status) {
      case 'PAID':                      this.step = 'success';                break;
      case 'FAILED_BalanceError':       this.step = 'failed_balance';         break;
      case 'FAILED_PasswordError':      this.step = 'failed_pin';             break;
      case 'FAILED_TimeOutError':       this.step = 'failed_timeout_singpay'; break;
      default:                          this.step = 'failed_timeout_singpay'; break;
    }
    this.stopPolling$.next();
  }

  ngOnDestroy(): void {
    this.stopPolling$.next();
    this.stopPolling$.complete();
  }
}
