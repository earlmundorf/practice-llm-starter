# Authentication — Context

## What This Flow Does

Manages OAuth2 authentication with SAP Commerce. Users log in with email/password, the app obtains a Bearer token, and all subsequent OCC API calls include the token in the Authorization header.

## When It's Used

- User clicks "Log In" from the header
- Accessing a protected page (cart, checkout, orders) while unauthenticated
- Token refresh when the current token expires
- Logging out (clearing token and cart state)

## Key Decisions

### OAuth2 password grant
SAP Commerce supports the OAuth2 password grant for customer login. The UI sends `grant_type=password` with `client_id`, `client_secret`, username, and password to the authorization server. This returns an `access_token` used for all OCC calls.

### Token storage
The access token is stored in `localStorage` for persistence across page refreshes. The token is read lazily (not on every render) and injected into API calls by the `api.ts` service layer. On logout, the token is cleared from localStorage.

### No refresh token handling (Phase 1)
For simplicity, the initial implementation doesn't handle token refresh. When the token expires (after ~12 hours), the user must log in again. Production should implement refresh token rotation.

### Protected route pattern
Routes that require authentication (checkout, orders) check `api.auth.isLoggedIn()` on mount. If not authenticated, the user is redirected to the login form with a `returnUrl` query parameter so they land back on the intended page after login.

### OAuth client
The UI uses the `trusted_client` OAuth client (configured in SAP Commerce sample data). The client ID and secret are embedded in the frontend code — this is acceptable for the password grant flow where the client credentials are not truly secret (browser-visible). Production deployments should use a backend-for-frontend (BFF) pattern.
