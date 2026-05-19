# Authentication — Components

> **Note:** `LoginForm.tsx` is planned — create it following the patterns in CLAUDE.md. The `AuthContext`, `api.ts` auth module, and related types already exist.

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/components/LoginForm.tsx` | Login form — email, password, submit, error display |
| `src/components/Header.tsx` | Shows login/logout button and user name when authenticated |
| `src/services/api.ts` | `api.auth.login()`, `api.auth.logout()`, `api.auth.isLoggedIn()`, `api.auth.getUser()` methods |
| `src/types/index.ts` | `User`, `AuthToken` types |

## How They Connect

```
Header.tsx
├── checks api.auth.isLoggedIn()
├── shows "Log In" button or user name + "Log Out"
├── login click: shows LoginForm modal or navigates to login
└── logout click: calls api.auth.logout(), dispatches 'authChanged'

LoginForm.tsx
├── form with email + password inputs
├── submit: calls api.auth.login(email, password)
├── on success: stores token, dispatches 'authChanged'
├── on error: shows error message
└── redirects to returnUrl or home

api.ts (auth module)
├── login(): POST /authorizationserver/oauth/token
├── logout(): clear localStorage token
├── isLoggedIn(): check token exists and not expired
└── getUser(): GET /users/current
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/authorizationserver/oauth/token` | OAuth2 token request (not under /occ) |
| GET | `/users/current?fields=FULL` | Get authenticated user profile |
