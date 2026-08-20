import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  form: FormGroup;
  loading  = signal(false);
  error    = signal<string | null>(null);
  success  = signal<string | null>(null);

  constructor(
    private fb:    FormBuilder,
    private auth:  AuthService,
    private router: Router,
    private route:  ActivatedRoute
  ) {
    this.form = this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });

    if (this.route.snapshot.queryParamMap.get('registered') === '1') {
      this.success.set('Compte créé avec succès. Vous pouvez maintenant vous connecter.');
    }
  }

  get email()    { return this.form.get('email')!; }
  get password() { return this.form.get('password')!; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.form.value;
    this.auth.login(email, password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/catalogue';
        // Si returnUrl est la landing page, aller directement au catalogue
        const destination = returnUrl === '/' ? '/catalogue' : returnUrl;
        this.router.navigateByUrl(destination);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Email ou mot de passe incorrect.');
      }
    });
  }
}
