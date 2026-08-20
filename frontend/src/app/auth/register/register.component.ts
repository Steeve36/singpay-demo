import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&_\-#])[A-Za-z\d@$!%*?&_\-#]{8,}$/;

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {
  form: FormGroup;
  loading = signal(false);
  error   = signal<string | null>(null);

  constructor(
    private fb:    FormBuilder,
    private auth:  AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(80)]],
      lastName:  ['', [Validators.required, Validators.maxLength(80)]],
      email:     ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      password:  ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]]
    });
  }

  get firstName() { return this.form.get('firstName')!; }
  get lastName()  { return this.form.get('lastName')!; }
  get email()     { return this.form.get('email')!; }
  get password()  { return this.form.get('password')!; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set(null);

    const { firstName, lastName, email, password } = this.form.value;
    this.auth.register(firstName, lastName, email, password).subscribe({
      next: () => this.router.navigate(['/login'], {
        queryParams: { registered: '1' }
      }),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Une erreur est survenue. Réessayez.');
      }
    });
  }
}
