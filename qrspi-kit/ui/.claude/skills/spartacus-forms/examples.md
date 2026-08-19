# Spartacus Form Examples

Focused code snippets showing correct and incorrect patterns for reactive form development.

---

## GOOD: Reactive Form with Validation and Error Display

Form uses `FormBuilder`, `CustomFormValidators`, and `<cx-form-errors>` for a complete, accessible form pattern.

```typescript
// feedback-form.component.ts
@Component({
  selector: 'cx-feedback-form',
  templateUrl: './feedback-form.component.html',
  styleUrls: ['./feedback-form.component.scss'],
})
export class FeedbackFormComponent {
  form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, CustomFormValidators.emailValidator]],
    subject: ['', Validators.required],
    message: ['', [Validators.required, Validators.minLength(10)]],
  });

  submitting = false;

  constructor(
    protected fb: FormBuilder,
    protected feedbackService: FeedbackService
  ) {}

  submit(): void {
    if (this.form.valid) {
      this.submitting = true;
      this.feedbackService.submit(this.form.value).subscribe({
        next: () => {
          this.form.reset();
          this.submitting = false;
        },
        error: () => {
          this.submitting = false;
        },
      });
    } else {
      this.form.markAllAsTouched();
    }
  }
}
```

```html
<!-- feedback-form.component.html -->
<form [formGroup]="form" (ngSubmit)="submit()">
  <label for="name">
    {{ 'feedback.name' | cxTranslate }}
  </label>
  <input
    id="name"
    type="text"
    formControlName="name"
    aria-required="true"
  />
  <cx-form-errors [control]="form.get('name')"></cx-form-errors>

  <label for="email">
    {{ 'feedback.email' | cxTranslate }}
  </label>
  <input
    id="email"
    type="email"
    formControlName="email"
    aria-required="true"
  />
  <cx-form-errors [control]="form.get('email')"></cx-form-errors>

  <label for="subject">
    {{ 'feedback.subject' | cxTranslate }}
  </label>
  <input
    id="subject"
    type="text"
    formControlName="subject"
    aria-required="true"
  />
  <cx-form-errors [control]="form.get('subject')"></cx-form-errors>

  <label for="message">
    {{ 'feedback.message' | cxTranslate }}
  </label>
  <textarea
    id="message"
    formControlName="message"
    aria-required="true"
  ></textarea>
  <cx-form-errors [control]="form.get('message')"></cx-form-errors>

  <button type="submit" [disabled]="form.invalid || submitting">
    {{ 'feedback.submit' | cxTranslate }}
  </button>
</form>
```

Why this is correct:
- Reactive form with `FormBuilder` — no template-driven `ngModel`
- `CustomFormValidators.emailValidator` for Spartacus-consistent email validation
- `<cx-form-errors>` for each field — automatic translated error messages
- Submit button disabled during submission to prevent double-submit
- `markAllAsTouched()` on invalid submit so errors appear immediately

---

## GOOD: Checkout Form Override

Custom address form component mapped via CMS config, extending the Spartacus original.

```typescript
// custom-address-form.component.ts
@Component({
  selector: 'cx-custom-address-form',
  templateUrl: './custom-address-form.component.html',
  styleUrls: ['./custom-address-form.component.scss'],
})
export class CustomAddressFormComponent extends AddressFormComponent {
  override ngOnInit(): void {
    super.ngOnInit();
    // Add a custom field to the existing form
    this.addressForm.addControl(
      'companyName',
      new FormControl('', Validators.maxLength(100))
    );
  }
}
```

```typescript
// custom-address-form.module.ts
@NgModule({
  declarations: [CustomAddressFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    I18nModule,
    FormErrorsModule,
    NgSelectModule,
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

Why this is correct:
- Extends `AddressFormComponent` — inherits all existing form setup and validation
- Adds custom field via `addControl()` instead of rebuilding the entire form
- CMS mapping replaces the original component cleanly — no template monkey-patching
- Module imports `FormErrorsModule` and `ReactiveFormsModule` as required

---

## BAD: Template-Driven Forms

```typescript
// bad-contact.component.ts
@Component({
  selector: 'cx-bad-contact',
  template: `
    <form #contactForm="ngForm" (ngSubmit)="submit()">
      <input [(ngModel)]="model.name" name="name" required />
      <input [(ngModel)]="model.email" name="email" required />
      <textarea [(ngModel)]="model.message" name="message" required></textarea>
      <button type="submit">Send</button>
    </form>
  `,
})
export class BadContactComponent {
  model = { name: '', email: '', message: '' };

  submit(): void {
    // sends model directly — no validation check
    this.contactService.send(this.model);
  }
}
```

**What's wrong:**
- Uses `[(ngModel)]` template-driven approach — Spartacus convention is reactive forms
- No `CustomFormValidators` — email validation is missing entirely
- No `<cx-form-errors>` — users get no feedback on what's wrong
- No `form.valid` check before submission — invalid data sent to backend
- No `FormGroup` — can't programmatically set errors from server responses

**Fix:** Rewrite with `FormBuilder`, `FormGroup`, `Validators`, `CustomFormValidators`, and `<cx-form-errors>` for each field.

---

## BAD: Missing Accessibility

```typescript
// bad-login.component.html
@Component({
  selector: 'cx-bad-login',
  template: `
    <form [formGroup]="form" (ngSubmit)="login()">
      <span>Email</span>
      <input formControlName="email" />
      <span>Password</span>
      <input type="password" formControlName="password" />
      <div class="error" *ngIf="form.get('email')?.errors?.['required']">
        Email is required
      </div>
      <button type="submit">Log In</button>
    </form>
  `,
})
export class BadLoginComponent { /* ... */ }
```

**What's wrong:**
- `<span>` used instead of `<label>` — no programmatic association between label and input
- Missing `for`/`id` binding — screen readers can't identify what each input is for
- No `aria-required="true"` on required fields
- Error messages not linked via `aria-describedby` — screen readers won't announce errors
- Hardcoded English strings instead of `cxTranslate`
- Uses `*ngIf` for error display instead of `<cx-form-errors>` which handles dirty/touched state

**Fix:** Use `<label for="email">`, add `id` to inputs, use `aria-required` and `aria-describedby`, replace error `<div>` with `<cx-form-errors>`, and pipe all strings through `cxTranslate`.

---

## GENERATE OUTPUT: /spartacus-forms generate contact-us

Running `/spartacus-forms generate contact-us` produces these files:

### contact-us-form.component.ts

```typescript
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CustomFormValidators } from '@spartacus/storefront';

@Component({
  selector: 'cx-contact-us-form',
  templateUrl: './contact-us-form.component.html',
  styleUrls: ['./contact-us-form.component.scss'],
})
export class ContactUsFormComponent {
  submitting = false;

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, CustomFormValidators.emailValidator]],
    subject: ['', [Validators.required, Validators.maxLength(200)]],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
  });

  constructor(protected fb: FormBuilder) {}

  submit(): void {
    if (this.form.valid) {
      this.submitting = true;
      // TODO: call facade service to submit form data
      // this.contactService.submit(this.form.value).subscribe(...)
    } else {
      this.form.markAllAsTouched();
      const firstInvalid = document.querySelector('form .ng-invalid') as HTMLElement;
      firstInvalid?.focus();
    }
  }
}
```

### contact-us-form.component.html

```html
<form [formGroup]="form" (ngSubmit)="submit()">
  <label for="contact-name">
    {{ 'contactUs.name' | cxTranslate }}
    <span class="cx-required" aria-hidden="true">*</span>
  </label>
  <input
    id="contact-name"
    type="text"
    formControlName="name"
    aria-required="true"
    [attr.aria-describedby]="form.get('name')?.invalid && form.get('name')?.touched ? 'contact-name-errors' : null"
  />
  <cx-form-errors
    [control]="form.get('name')"
    id="contact-name-errors"
  ></cx-form-errors>

  <label for="contact-email">
    {{ 'contactUs.email' | cxTranslate }}
    <span class="cx-required" aria-hidden="true">*</span>
  </label>
  <input
    id="contact-email"
    type="email"
    formControlName="email"
    aria-required="true"
    [attr.aria-describedby]="form.get('email')?.invalid && form.get('email')?.touched ? 'contact-email-errors' : null"
  />
  <cx-form-errors
    [control]="form.get('email')"
    id="contact-email-errors"
  ></cx-form-errors>

  <label for="contact-subject">
    {{ 'contactUs.subject' | cxTranslate }}
    <span class="cx-required" aria-hidden="true">*</span>
  </label>
  <input
    id="contact-subject"
    type="text"
    formControlName="subject"
    aria-required="true"
    [attr.aria-describedby]="form.get('subject')?.invalid && form.get('subject')?.touched ? 'contact-subject-errors' : null"
  />
  <cx-form-errors
    [control]="form.get('subject')"
    id="contact-subject-errors"
  ></cx-form-errors>

  <label for="contact-message">
    {{ 'contactUs.message' | cxTranslate }}
    <span class="cx-required" aria-hidden="true">*</span>
  </label>
  <textarea
    id="contact-message"
    formControlName="message"
    rows="6"
    aria-required="true"
    [attr.aria-describedby]="form.get('message')?.invalid && form.get('message')?.touched ? 'contact-message-errors' : null"
  ></textarea>
  <cx-form-errors
    [control]="form.get('message')"
    id="contact-message-errors"
  ></cx-form-errors>

  <button
    type="submit"
    class="btn btn-primary"
    [disabled]="form.invalid || submitting"
  >
    {{ 'contactUs.submit' | cxTranslate }}
  </button>
</form>
```

### contact-us-form.component.scss

```scss
%cx-contact-us-form {
  :host {
    display: block;
    max-width: 600px;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  label {
    font-weight: 600;
  }

  .cx-required {
    color: var(--cx-color-danger);
  }

  input,
  textarea {
    width: 100%;
    padding: 0.5rem;
    border: 1px solid var(--cx-color-light);
    border-radius: 4px;
  }
}
```

### contact-us-form.module.ts

```typescript
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { I18nModule } from '@spartacus/core';
import { FormErrorsModule } from '@spartacus/storefront';
import { ContactUsFormComponent } from './contact-us-form.component';

@NgModule({
  declarations: [ContactUsFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    I18nModule,
    FormErrorsModule,
  ],
  exports: [ContactUsFormComponent],
})
export class ContactUsFormModule {}
```

### contact-us-form.component.spec.ts

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { I18nTestingModule } from '@spartacus/core';
import { FormErrorsModule } from '@spartacus/storefront';
import { ContactUsFormComponent } from './contact-us-form.component';

describe('ContactUsFormComponent', () => {
  let component: ContactUsFormComponent;
  let fixture: ComponentFixture<ContactUsFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ContactUsFormComponent],
      imports: [
        ReactiveFormsModule,
        I18nTestingModule,
        FormErrorsModule,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactUsFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should create form with 4 controls', () => {
    expect(component.form.contains('name')).toBeTruthy();
    expect(component.form.contains('email')).toBeTruthy();
    expect(component.form.contains('subject')).toBeTruthy();
    expect(component.form.contains('message')).toBeTruthy();
  });

  it('should require name', () => {
    const control = component.form.get('name');
    control?.setValue('');
    expect(control?.valid).toBeFalsy();
    control?.setValue('John Doe');
    expect(control?.valid).toBeTruthy();
  });

  it('should validate email format', () => {
    const control = component.form.get('email');
    control?.setValue('not-an-email');
    expect(control?.valid).toBeFalsy();
    control?.setValue('user@example.com');
    expect(control?.valid).toBeTruthy();
  });

  it('should not submit when form is invalid', () => {
    component.submit();
    expect(component.submitting).toBeFalsy();
  });

  it('should render submit button as disabled when form is invalid', () => {
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button.disabled).toBeTruthy();
  });
});
```
