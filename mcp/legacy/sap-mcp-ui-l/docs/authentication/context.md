# Authentication — Context

## What This Flow Does

Manages OAuth2 authentication with SAP Commerce. Users select a demo account or enter credentials manually, the app obtains a Bearer token via the password grant, and all subsequent OCC API calls include the token in the Authorization header. On startup, unauthenticated users are immediately presented with a login modal.

## When It's Used

- App startup when no token exists in localStorage
- User clicks "Log In" from the header (desktop or mobile)
- User clicks "Switch Account" from the user dropdown
- Logging out (clearing token, email, refresh token, and cart code)
- Any `authFetch` call that receives a 401 response (auto-logout)

## Key Decisions

### OAuth2 password grant
SAP Commerce supports the OAuth2 password grant for customer login. The UI sends `grant_type=password` with `client_id`, `client_secret`, username, and password to `/authorizationserver/oauth/token`. This returns an `access_token` (and optionally a `refresh_token`) used for all authenticated OCC calls.

### Token storage in localStorage
Four keys are persisted in localStorage: `occ_access_token`, `occ_refresh_token`, `occ_user_email`, and `occ_cart_code`. The token is read lazily by `authFetch` and injected as a Bearer header. On logout, all four keys are removed. This provides persistence across page refreshes without any server-side session.

### No refresh token rotation (current implementation)
The refresh token is stored if the server returns one, but it is never used to obtain a new access token. When the token expires, the next API call returns a 401, which triggers automatic logout and forces the user to log in again. Production should implement refresh token rotation.

### Demo user quick-login
The `UserPicker` component provides three hardcoded demo users (john.doe, jane.smith, bob.wilson) with one-click login buttons. This accelerates development and demos without requiring manual credential entry. A manual email/password form is also available below the quick-login buttons.

### Startup login gate
`App.tsx` checks `auth.isLoggedIn()` on mount. If no token exists, the `UserPicker` modal is shown immediately as a full-screen overlay. The cancel button is only available if the user is already authenticated (e.g., switching accounts). This ensures the user is always logged in before interacting with the app.

### Event-driven auth state propagation
Auth state changes are broadcast via `window.dispatchEvent(new Event('authChanged'))` rather than React context. The `Header` component listens for this event to reload the current user and update the avatar/dropdown. This keeps auth state decoupled from the component tree.

### OAuth client
The UI uses the `trusted_client` OAuth client (configured in SAP Commerce sample data). The client ID and secret are embedded in the frontend code — this is acceptable for the password grant flow where the client credentials are not truly secret (browser-visible). Production deployments should use a backend-for-frontend (BFF) pattern.

### Cart code cleared on login
When a user logs in, the stored `occ_cart_code` is removed from localStorage. This ensures `ensureCart` creates or discovers the correct cart for the newly authenticated user, rather than reusing a stale cart from a previous session.
