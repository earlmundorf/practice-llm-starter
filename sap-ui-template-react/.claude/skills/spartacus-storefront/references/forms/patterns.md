# Spartacus Form Patterns

Reference snippets for reactive form development in Spartacus 6.x with NgModules.

---

## Basic Reactive Form

Use `FormBuilder.group()` to define the form shape with validators. Spartacus provides `CustomFormValidators` for common storefront validations alongside Angular's built-in `Validators`.

```typescript
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CustomFormValidators } from '@spartacus/storefront';

@Component({
  selector: 'cx-newsletter-form',
  templateUrl: './newsletter-form.component.html',
})
export class NewsletterFormComponent {
  form: FormGroup = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    email: ['', [Validators.required, CustomFormValidators.emailValidator]],
  });

  constructor(protected fb: FormBuilder) {}

  submit(): void {
    if (this.form.valid) {
      // dispatch action or call facade
    }
  }
}
```

Template with reactive form bindings:

```html
<form [formGroup]="form" (ngSubmit)="submit()">
  <label for="firstName">
    {{ 'newsletter.firstName' | cxTranslate }}
  </label>
  <input
    id="firstName"
    type="text"
    formControlName="firstName"
    aria-required="true"
  />
  <cx-form-errors [control]="form.get('firstName')"></cx-form-errors>

  <label for="email">
    {{ 'newsletter.email' | cxTranslate }}
  </label>
  <input
    id="email"
    type="email"
    formControlName="email"
    aria-required="true"
  />
  <cx-form-errors [control]="form.get('email')"></cx-form-errors>

  <button type="submit" [disabled]="form.invalid">
    {{ 'newsletter.submit' | cxTranslate }}
  </button>
</form>
```

---

## CustomFormValidators

Spartacus provides reusable validators in `CustomFormValidators` from `@spartacus/storefront`. Use these instead of writing custom regex validators for common patterns.

```typescript
import { CustomFormValidators } from '@spartacus/storefront';

// Email validation — checks RFC-compliant email format
email: ['', [Validators.required, CustomFormValidators.emailValidator]],

// Password validation — enforces Spartacus password policy
password: ['', [Validators.required, CustomFormValidators.passwordValidator]],

// Pattern-based validation — wraps Validators.pattern with Spartacus error key
phone: ['', [CustomFormValidators.patternValidation(
  /^\+?[0-9\s\-()]{7,15}$/,
  'phone'
)]],
```

`CustomFormValidators` returns validation errors keyed in a way that `<cx-form-errors>` understands automatically — no manual error message mapping needed.

---

## Form Error Display

The `<cx-form-errors>` component from `@spartacus/storefront` handles error message rendering. It reads the control's validation errors and displays the appropriate translated message.

```html
<!-- Basic usage — pass the AbstractControl -->
<cx-form-errors [control]="form.get('email')"></cx-form-errors>

<!-- Works with nested form groups -->
<div formGroupName="address">
  <input formControlName="city" />
  <cx-form-errors [control]="form.get('address.city')"></cx-form-errors>
</div>
```

Import `FormErrorsModule` in your feature module:

```typescript
import { FormErrorsModule } from '@spartacus/storefront';

@NgModule({
  imports: [
    ReactiveFormsModule,
    FormErrorsModule,  // provides <cx-form-errors>
  ],
})
export class MyFormModule {}
```

`cx-form-errors` only displays errors when the control is dirty or touched — no premature error flash on initial load.

---

## Checkout Address Form Override

Replace the Spartacus `AddressFormComponent` with a custom implementation via CMS component mapping. Never modify the Spartacus template directly.

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import {
  I18nModule,
  provideDefaultConfig,
  CmsConfig,
} from '@spartacus/core';
import { FormErrorsModule } from '@spartacus/storefront';
import { CustomAddressFormComponent } from './custom-address-form.component';

@NgModule({
  declarations: [CustomAddressFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    I18nModule,
    FormErrorsModule,
  ],
  providers: [
    provideDefaultConfig(<CmsConfig>{
      cmsComponents: {
        AddressFormComponent: {
          component: CustomAddressFormComponent,
        },
      },
    }),
  ],
})
export class CustomAddressFormModule {}
```

The custom component can extend the original to reuse its form setup:

```typescript
import { Component } from '@angular/core';
import { AddressFormComponent } from '@spartacus/storefront';

@Component({
  selector: 'cx-custom-address-form',
  templateUrl: './custom-address-form.component.html',
})
export class CustomAddressFormComponent extends AddressFormComponent {
  // Add custom fields or override validation logic
}
```

---

## Multi-Step Form Pattern

For multi-step checkout or registration flows, persist intermediate state in a facade service — not in URL query params or local component state that would be lost on navigation.

```typescript
@Injectable({ providedIn: 'root' })
export class RegistrationFacade {
  private stepsSubject = new BehaviorSubject<Partial<RegistrationModel>>({});
  steps$ = this.stepsSubject.asObservable();

  updateStep(step: Partial<RegistrationModel>): void {
    this.stepsSubject.next({ ...this.stepsSubject.value, ...step });
  }

  reset(): void {
    this.stepsSubject.next({});
  }
}
```

Each step component validates its own portion, then updates the facade:

```typescript
@Component({ /* ... */ })
export class Step1Component {
  form: FormGroup = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
  });

  constructor(
    protected fb: FormBuilder,
    protected registrationFacade: RegistrationFacade,
    protected router: Router
  ) {}

  next(): void {
    if (this.form.valid) {
      this.registrationFacade.updateStep(this.form.value);
      this.router.navigate(['/register/step-2']);
    }
  }
}
```

---

## Form Accessibility

Every form input must have an associated label, appropriate ARIA attributes, and proper error association for screen readers.

```html
<!-- Explicit label binding via for/id -->
<label for="firstName">
  {{ 'form.firstName' | cxTranslate }}
  <span class="cx-required" aria-hidden="true">*</span>
</label>
<input
  id="firstName"
  type="text"
  formControlName="firstName"
  aria-required="true"
  [attr.aria-describedby]="form.get('firstName')?.invalid ? 'firstName-errors' : null"
/>
<cx-form-errors
  [control]="form.get('firstName')"
  id="firstName-errors"
></cx-form-errors>
```

Move focus to the first invalid field on submission failure:

```typescript
submit(): void {
  if (this.form.valid) {
    this.onSubmit();
  } else {
    this.form.markAllAsTouched();
    const firstInvalid = document.querySelector('form .ng-invalid') as HTMLElement;
    firstInvalid?.focus();
  }
}
```

Use `aria-live="polite"` for dynamic error summaries:

```html
<div aria-live="polite" class="sr-only">
  @if (form.invalid && form.dirty) {
    {{ 'form.errorsPresent' | cxTranslate }}
  }
</div>
```

---

## Server-Side Validation Handling

Map OCC backend error responses to form control errors so users see contextual messages next to the relevant field.

```typescript
import { HttpErrorResponse } from '@angular/common/http';

handleServerErrors(error: HttpErrorResponse): void {
  if (error.error?.errors) {
    for (const err of error.error.errors) {
      // OCC errors include a subject field matching the form field name
      const control = this.form.get(err.subject);
      if (control) {
        control.setErrors({ serverError: err.message });
        control.markAsTouched();
      }
    }
  }
}
```

In the template, display server errors alongside validation errors:

```html
<cx-form-errors [control]="form.get('email')"></cx-form-errors>
@if (form.get('email')?.hasError('serverError')) {
  <div class="cx-server-error" role="alert">
    {{ form.get('email')?.getError('serverError') }}
  </div>
}
```

Subscribe to the submission result and handle errors:

```typescript
this.myFacade.submitForm(this.form.value).pipe(
  catchError((error: HttpErrorResponse) => {
    this.handleServerErrors(error);
    return EMPTY;
  })
).subscribe(() => {
  this.form.reset();
  // navigate to success page
});
```
