# ThinkShop UI — Project Setup

Setup guide based on the reference app's actual stack. The reference
app is available locally at `ui/sample/thinkshop-frontend/`.

## Prerequisites

- Node.js 20+ (LTS)
- npm

## Tech Stack (from reference `package.json`)

```
react           ^19.2.0
react-dom       ^19.2.0
react-router-dom ^7.9.6
tailwindcss     ^4.1.17
vite            ^7.2.4
typescript      ^5.9.3
eslint          ^9.39.1
```

## Initialize Project

Option A — Copy from reference and adapt:

```bash
cd ui
cp -r sample/thinkshop-frontend/{src,public,index.html,package.json,tsconfig*.json,vite.config.ts,postcss.config.js,eslint.config.js} .
npm install
```

Option B — Fresh init matching reference versions:

```bash
cd ui
npm create vite@latest . -- --template react-ts
npm install react-router-dom
npm install -D tailwindcss @tailwindcss/postcss autoprefixer postcss
```

## Configuration Files

### `vite.config.ts`

Adapt proxy from reference (change `/api` to SAP Commerce endpoints):

```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/occ': {
        target: 'http://localhost:9001',
        changeOrigin: true,
      },
      '/authorizationserver': {
        target: 'http://localhost:9001',
        changeOrigin: true,
      },
    },
  },
})
```

### `postcss.config.js` (from reference)

```js
export default {
  plugins: {
    '@tailwindcss/postcss': {},
    autoprefixer: {},
  },
}
```

### `src/index.css` (from reference)

```css
@import "tailwindcss";

@variant dark (.dark &);

/* Custom animations kept from reference */
```

## Project Structure

Based on reference app structure, with additions for MCP/auth:

```
ui/
+-- docs/                           # This documentation
+-- sample/thinkshop-frontend/      # Reference app (read-only)
+-- public/
|   +-- favicon.svg
+-- src/
|   +-- main.tsx                    # Entry point (from reference)
|   +-- App.tsx                     # Router + layout (from reference)
|   +-- App.css                     # (empty — Tailwind handles styling)
|   +-- index.css                   # Tailwind + custom animations
|   |
|   +-- components/                 # From reference
|   |   +-- Header.tsx              # Desktop + mobile nav, cart badge
|   |   +-- CartModal.tsx           # Slide-out cart drawer
|   |   +-- ProductCard.tsx         # Product card for grid/list
|   |   +-- Toast.tsx               # Notification toasts
|   |   +-- LoginForm.tsx           # NEW: OAuth2 login (replaces UserPicker)
|   |   +-- SearchBar.tsx           # NEW: Product search input
|   |   +-- Pagination.tsx          # NEW: Page controls
|   |   +-- ProductImage.tsx        # NEW: Image with fallback
|   |   +-- StarRating.tsx          # NEW: Product ratings
|   |   +-- CheckoutSteps.tsx       # NEW: Multi-step checkout
|   |
|   +-- pages/                      # From reference
|   |   +-- Home.tsx                # Hero + features (as-is)
|   |   +-- Products.tsx            # Product grid (adapted for MCP)
|   |   +-- Chat.tsx                # AI chat (adapted for Claude API + MCP)
|   |   +-- Checkout.tsx            # Checkout flow (adapted for MCP)
|   |   +-- OrderConfirmation.tsx   # Order success (adapted for MCP)
|   |   +-- Orders.tsx              # Order history (adapted for MCP)
|   |
|   +-- contexts/                   # From reference
|   |   +-- DarkModeContext.tsx      # Dark mode toggle (as-is)
|   |   +-- AuthContext.tsx          # NEW: OAuth2 auth state
|   |
|   +-- services/                   # Replaces reference api.ts
|   |   +-- mcp-client.ts           # NEW: MCP JSON-RPC client
|   |   +-- auth.ts                 # NEW: OAuth2 token management
|   |   +-- llm.ts                  # NEW: Claude API integration
|   |
|   +-- types/
|   |   +-- index.ts                # Adapted from reference types
|   |   +-- mcp.ts                  # NEW: JSON-RPC, MCP types
|   |   +-- commerce.ts             # NEW: SAP Commerce data shapes
|   |
|   +-- hooks/                      # NEW
|       +-- useMcp.ts               # MCP session management
|       +-- useAuth.ts              # Login/logout/token
|
+-- index.html
+-- package.json
+-- tsconfig.json
+-- tsconfig.node.json
+-- vite.config.ts
+-- postcss.config.js
+-- eslint.config.js
+-- .env.local                      # Secrets (gitignored)
+-- .env.example                    # Template
+-- .gitignore
```

## Key New Files

### `src/services/mcp-client.ts`

```ts
const MCP_PROTOCOL_VERSION = '2025-11-25';

interface McpClientConfig {
  baseSiteId: string;
  getToken: () => string | null;
}

export class McpClient {
  private sessionId: string | null = null;
  private baseUrl: string;
  private getToken: () => string | null;

  constructor(config: McpClientConfig) {
    this.baseUrl = `/occ/v2/${config.baseSiteId}/mcp`;
    this.getToken = config.getToken;
  }

  async initialize(): Promise<void> {
    const response = await this.post({
      method: 'initialize',
      params: {
        protocolVersion: MCP_PROTOCOL_VERSION,
        capabilities: {},
        clientInfo: { name: 'thinkshop', version: '1.0.0' },
      },
    }, { 'MCP-Protocol-Version': MCP_PROTOCOL_VERSION });

    this.sessionId = response.headers.get('MCP-Session-Id');

    // Send initialized notification
    await this.post({ method: 'notifications/initialized' });
  }

  async callTool(name: string, args: Record<string, unknown> = {}): Promise<unknown> {
    const response = await this.post({
      method: 'tools/call',
      params: { name, arguments: args },
    });
    const json = await response.json();
    if (json.error) throw new Error(json.error.message);
    const text = json.result.content[0].text;
    return JSON.parse(text);
  }

  async listTools(): Promise<unknown[]> {
    const response = await this.post({ method: 'tools/list' });
    const json = await response.json();
    return json.result.tools;
  }

  async terminate(): Promise<void> {
    if (!this.sessionId) return;
    await fetch(this.baseUrl, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${this.getToken()}`,
        'MCP-Session-Id': this.sessionId,
      },
    });
    this.sessionId = null;
  }

  private async post(body: Record<string, unknown>, extraHeaders: Record<string, string> = {}): Promise<Response> {
    const id = Math.floor(Math.random() * 1000000);
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${this.getToken()}`,
      ...extraHeaders,
    };
    if (this.sessionId) {
      headers['MCP-Session-Id'] = this.sessionId;
    }
    return fetch(this.baseUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify({ jsonrpc: '2.0', id, ...body }),
    });
  }
}
```

### `src/services/auth.ts`

```ts
interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  refresh_token?: string;
}

export class AuthService {
  private token: string | null = null;
  private refreshToken: string | null = null;
  private expiresAt: number = 0;

  async login(username: string, password: string): Promise<void> {
    const response = await fetch('/authorizationserver/oauth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: import.meta.env.VITE_OAUTH_CLIENT_ID || 'mobile_android',
        client_secret: import.meta.env.VITE_OAUTH_CLIENT_SECRET || 'secret',
        username,
        password,
      }),
    });
    if (!response.ok) throw new Error('Login failed');
    const data: TokenResponse = await response.json();
    this.setTokens(data);
  }

  getToken(): string | null {
    if (this.token && Date.now() < this.expiresAt) return this.token;
    return null;
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  logout(): void {
    this.token = null;
    this.refreshToken = null;
    this.expiresAt = 0;
  }

  private setTokens(data: TokenResponse): void {
    this.token = data.access_token;
    this.refreshToken = data.refresh_token || null;
    this.expiresAt = Date.now() + data.expires_in * 1000;
  }
}
```

### `src/types/commerce.ts`

Types matching SAP Commerce OCC data shapes:

```ts
export interface CommerceProduct {
  code: string;
  name: string;
  description?: string;
  price: { value: number; currencyIso: string; formattedValue: string };
  stock: { stockLevel: number; stockLevelStatus: string };
  averageRating?: number;
  images?: { format: string; url: string }[];
  categories?: { code: string; name: string }[];
}

export interface CommerceCart {
  code: string;
  totalItems: number;
  totalPrice: { value: number; formattedValue: string };
  totalUnitCount: number;
  entries: CommerceCartEntry[];
}

export interface CommerceCartEntry {
  entryNumber: number;
  product: { code: string; name: string };
  quantity: number;
  totalPrice: { value: number; formattedValue?: string };
}

export interface CommerceOrder {
  code: string;
  status: string;
  statusDisplay: string;
  created: string;
  totalPrice: { value: number; formattedValue: string };
  entries: CommerceCartEntry[];
  deliveryAddress?: CommerceAddress;
}

export interface CommerceAddress {
  firstName: string;
  lastName: string;
  line1: string;
  line2?: string;
  town: string;
  postalCode: string;
  country: { isocode: string };
}

export interface SearchResult {
  products: CommerceProduct[];
  pagination: {
    currentPage: number;
    pageSize: number;
    totalResults: number;
    totalPages: number;
  };
  sorts: { code: string; name: string; selected: boolean }[];
}
```

## Environment Variables

**`.env.example`:**

```bash
# SAP Commerce base site
VITE_BASE_SITE_ID=electronics

# OAuth2 credentials
VITE_OAUTH_CLIENT_ID=mobile_android
VITE_OAUTH_CLIENT_SECRET=secret

# Claude API (for Chat page)
VITE_ANTHROPIC_API_KEY=sk-ant-...
```

## Development

```bash
cd ui
npm install
npm run dev        # Dev server on :3000, proxies to Commerce :9001
npm run build      # Production build to dist/
npm run preview    # Preview production build
npm run lint       # ESLint
```

## Proxy Setup (Development)

```
  Browser :3000  -->  Vite Dev Server  -->  SAP Commerce :9001
    /occ/v2/*         proxy                  /occ/v2/*
    /authorizationserver/*                   /authorizationserver/*
```

## Mapping Reference API to MCP Tools

| Reference `api.ts` method | MCP Tool | Notes |
|---------------------------|----------|-------|
| `api.getProducts()` | `product_search` | Returns paginated, need query param |
| `api.addToCart()` | `cart_add_product` | Uses productCode not productId |
| `api.getCart()` | `cart_get` | No userId param (uses auth token) |
| `api.updateCartItem()` | — | Not yet defined, may need new tool |
| `api.removeFromCart()` | — | Not yet defined, may need new tool |
| `api.clearCart()` | — | Not yet defined, may need new tool |
| `api.createOrder()` | `order_place` | Preceded by checkout_set_* tools |
| `api.getOrder()` | `order_get` | Uses order code |
| `api.getUserOrders()` | `order_history` | Paginated, filter by status |
| `api.cancelOrder()` | — | Not yet defined |
| `api.sendChatMessage()` | Claude API + MCP tools | No backend chat endpoint |
| `api.getChatSuggestions()` | Client-side logic | Generate from conversation |
| `api.createUser()` | — | OAuth2 login replaces user creation |
| `api.getUser()` | `customer_get` | Current authenticated user |
| `api.getAllUsers()` | — | Not needed with OAuth2 |

Missing tools to consider for Phase 2 coremcp:
- `cart_update_quantity` — Update existing cart entry quantity
- `cart_remove_product` — Remove entry from cart
- `cart_clear` — Clear all cart entries
- `order_cancel` — Cancel a pending order
