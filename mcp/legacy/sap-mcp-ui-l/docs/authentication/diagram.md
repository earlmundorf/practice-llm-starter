# Authentication — Diagrams

## Login Flow

Shows both the quick-login (demo user button) and manual form paths. Both converge on `auth.login()` which obtains the OAuth2 token.

```mermaid
sequenceDiagram
    participant User
    participant Picker as UserPicker.tsx
    participant API as api.ts
    participant SAP as SAP Commerce Auth

    alt Quick login
        User->>Picker: clicks demo user button
        Picker->>Picker: pre-fills email + password
    else Manual login
        User->>Picker: enters email + password
        User->>Picker: clicks "Log In"
    end
    Picker->>Picker: disable buttons, show "Logging in..."
    Picker->>API: auth.login(email, password)
    API->>SAP: POST /authorizationserver/oauth/token
    Note right of API: grant_type=password<br/>client_id=trusted_client<br/>client_secret=secret
    SAP-->>API: { access_token, refresh_token, expires_in }
    API->>API: store token + email in localStorage
    API->>API: clear stored cart code
    API->>API: dispatchEvent('authChanged')
    API-->>Picker: success
    Picker->>API: api.getUser()
    API->>SAP: GET /users/current?fields=FULL
    SAP-->>API: { uid, firstName, lastName }
    API-->>Picker: User object
    Picker->>Picker: onUserSelected(user)
```

## Startup Auth Gate

On app load, `App.tsx` checks for an existing token. If none exists, the user must log in before interacting with the app.

```mermaid
sequenceDiagram
    participant Browser
    participant App as App.tsx
    participant Auth as api.auth
    participant Picker as UserPicker.tsx

    Browser->>App: initial render
    App->>Auth: isLoggedIn()
    alt no token in localStorage
        Auth-->>App: false
        App->>Picker: render (onCancel=null)
        Note right of Picker: Cancel button hidden —<br/>user must log in
        Picker-->>App: onUserSelected(user)
        App->>App: hide UserPicker overlay
    else token exists
        Auth-->>App: true
        App->>App: render app normally
    end
```

## Token Lifecycle

Shows how the token flows from login through API calls to eventual expiry or logout.

```mermaid
graph TD
    Login[UserPicker Login] -->|POST /oauth/token| Token[Access Token]
    Token -->|stored in| LS[localStorage<br/>occ_access_token]
    LS -->|read by| AuthFetch[authFetch wrapper]
    AuthFetch -->|Authorization: Bearer| OCC[OCC API Calls]
    OCC -->|200 OK| Success[Normal Response]
    OCC -->|401 Unauthorized| AutoLogout[auth.logout]
    Logout[User clicks Logout] --> ClearLS[Clear localStorage<br/>token + email + cart code]
    AutoLogout --> ClearLS
    ClearLS -->|dispatchEvent| AuthChanged['authChanged' event]
    AuthChanged --> HeaderUpdate[Header reloads user state]
    AuthChanged --> ShowPicker[UserPicker shown]
```

## Auth State Propagation

Shows how auth changes propagate across components without React context, using DOM events.

```mermaid
graph LR
    subgraph Triggers
        Login[auth.login]
        Logout[auth.logout]
    end

    Login -->|dispatchEvent| E['authChanged' event]
    Logout -->|dispatchEvent| E

    E --> Header[Header.tsx<br/>reloads user via api.getUser]
    E --> Cart[Cart count reset]

    Header -->|updates| Avatar[Avatar + Dropdown]
    Header -->|updates| LoginBtn[Log In Button]

    Logout -->|also dispatches| CE['cartUpdated' event]
    CE --> CartBadge[Cart badge count]
```
