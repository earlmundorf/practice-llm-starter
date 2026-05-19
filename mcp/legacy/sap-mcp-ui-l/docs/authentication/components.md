# Authentication — Components

## Files That Implement This Flow

| File | Purpose |
|------|---------|
| `src/services/api.ts` | `auth.login()`, `auth.logout()`, `auth.isLoggedIn()`, `auth.getToken()`, `auth.getUserEmail()` + `authFetch` wrapper that injects Bearer token and handles 401 auto-logout |
| `src/components/UserPicker.tsx` | Login modal — demo user quick-login buttons, manual email/password form, error display |
| `src/components/Header.tsx` | Shows avatar + dropdown (profile, switch account, logout) when authenticated; shows "Log In" button when not; listens for `authChanged` events |
| `src/App.tsx` | Startup login gate — shows `UserPicker` overlay if no token exists on mount |
| `src/types/index.ts` | `User` type used by auth callbacks |

## How They Connect

```
App.tsx
├── checks auth.isLoggedIn() on mount
├── if false: renders UserPicker as full-screen overlay
├── UserPicker.onUserSelected: hides overlay
└── UserPicker.onCancel: only available if already logged in

UserPicker.tsx
├── DEMO_USERS: 3 hardcoded accounts with quick-login buttons
├── manual form: email + password inputs
├── submit/quick-login: calls auth.login(email, password)
├── on success: calls api.getUser(), passes User to onUserSelected
└── on error: shows inline error message

Header.tsx
├── listens for 'authChanged' event → reloads user via api.getUser()
├── when logged in: avatar with initials, dropdown menu
│   ├── My Profile → /users
│   ├── Switch Account → opens UserPicker modal
│   └── Logout → calls auth.logout(), shows UserPicker
├── when not logged in: "Log In" button → opens UserPicker modal
└── on logout: clears currentUser, resets cart count, dispatches cartUpdated

api.ts (auth module)
├── login(): POST /authorizationserver/oauth/token, stores token + email, clears cart code, dispatches authChanged
├── logout(): removes all 4 localStorage keys, dispatches authChanged
├── getToken(): reads occ_access_token from localStorage
├── getUserEmail(): reads occ_user_email from localStorage
├── isLoggedIn(): returns true if token exists
└── authFetch(): wraps fetch with Bearer header, auto-logout on 401
```

## OCC Endpoints Used

| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/authorizationserver/oauth/token` | OAuth2 token request (not under /occ) |
| GET | `/occ/v2/{site}/users/current?fields=FULL` | Get authenticated user profile (called after login) |
