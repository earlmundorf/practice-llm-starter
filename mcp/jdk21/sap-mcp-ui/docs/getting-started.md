# Getting Started — Local Development

This guide walks you through setting up the SAP MCP UI for local development. The UI is a React storefront that connects to the SAP Commerce MCP Server backend via OCC REST APIs.

## Prerequisites

### SAP Commerce MCP Server (required)

The UI is a frontend for the SAP Commerce MCP Server. **You must have the MCP Server running locally before the UI will work.** Follow the [SAP-MCP-Server getting started guide](https://github.com/jonmundorf-cg/SAP-MCP-Server/blob/main/docs/getting-started.md) to set it up first.

**Verify the backend is running:**
```bash
curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15
```

If you get a JSON response with product data, the backend is ready.

### Node.js 20+

The UI requires Node.js 20 or later (LTS recommended).

**Check your version:**
```bash
node --version
```

**Install via Homebrew (macOS):**
```bash
brew install node@20
```

**Or use nvm (any platform):**
```bash
nvm install 20
nvm use 20
```

### npm

Comes with Node.js. Verify:
```bash
npm --version
```

## 1. Clone the Repository

```bash
git clone <this-repo-url>
cd SAP-MCP-UI
```

## 2. Install Dependencies

```bash
npm install
```

This installs React 19, Tailwind CSS 4, Vite 7, React Router 7, and all other dependencies.

## 3. Configure Environment

The project includes two environment files:

| File | Purpose | Committed |
|------|---------|-----------|
| `.env.example` | Template with all available variables | Yes |
| `.env.development` | Dev defaults (used by `npm run dev`) | Yes |
| `.env` | Your local overrides | No (gitignored) |
| `.env.local` | Your local secrets | No (gitignored) |

**For standard local development, no configuration is needed.** The defaults in `.env.development` point to the local SAP Commerce server.

If you need to override anything, copy the example file:
```bash
cp .env.example .env
```

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `/occ/v2/electronics` | OCC API base path (proxied to SAP Commerce in dev) |

The Vite dev server proxies `/occ/*` and `/authorizationserver/*` requests to `https://localhost:9002` (the SAP Commerce HTTPS port). This is configured in `vite.config.ts` and requires no setup on your part.

## 4. Start the Dev Server

```bash
npm run dev
```

The UI starts at **http://localhost:5173** with hot module replacement (HMR).

**What happens on startup:**
1. Vite compiles the React/TypeScript application
2. The dev server starts on port 5173
3. API requests to `/occ/*` and `/authorizationserver/*` are proxied to `https://localhost:9002`
4. The browser auto-opens (or navigate to http://localhost:5173 manually)

## 5. Verify Everything Works

### Browse Products

Navigate to the Products page. You should see 10 electronics products with images, prices, and descriptions. If the product list is empty, make sure:
1. The SAP Commerce server is running (`curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15`)
2. Solr has been indexed (`./local/scripts/index-solr.sh` in the MCP Server project)

### Log In

Use one of the test accounts:

| Email | Password |
|-------|----------|
| john.doe@thinkshop.com | 1234 |
| jane.smith@thinkshop.com | 1234 |
| bob.wilson@thinkshop.com | 1234 |

**OAuth client used by the UI:** `trusted_client` / `secret` (configured in `vite.config.ts` proxy and `services/auth.ts`)

### Add to Cart and Checkout

1. Browse products and add one to your cart
2. Open the cart and proceed to checkout
3. Complete the checkout flow (address, delivery mode, payment, place order)
4. View the order in Order History

## 6. Project Structure

```
SAP-MCP-UI/
├── src/
│   ├── components/        # Reusable UI components (Header, CartModal, ProductCard, etc.)
│   ├── pages/             # Page-level components (Home, Products, Checkout, Orders, etc.)
│   ├── services/api.ts    # OCC API client with OAuth2 token management
│   ├── contexts/          # React Context providers (auth, cart, dark mode)
│   ├── hooks/             # Custom React hooks
│   └── types/             # TypeScript type definitions
├── docs/                  # Architecture and planning docs
├── public/                # Static assets
├── vite.config.ts         # Vite config with SAP Commerce API proxy
├── .env.example           # Environment variable template
├── .env.development       # Dev environment defaults
└── package.json
```

## 7. How the Proxy Works

In development, the Vite dev server proxies API requests to SAP Commerce:

```
Browser :5173  -->  Vite Dev Server  -->  SAP Commerce :9002 (HTTPS)
  /occ/v2/*           proxy                /occ/v2/*
  /authorizationserver/*                   /authorizationserver/*
```

The proxy is configured in `vite.config.ts`:
- Targets `https://localhost:9002` (SAP Commerce HTTPS port)
- Sets `secure: false` to accept the self-signed certificate
- Strips the `Origin` header (SAP Commerce CORS filter rejects unknown origins in dev)

**In production,** there is no proxy. The built app makes direct requests to the API URL specified by `VITE_API_URL` at build time.

## 8. Available Commands

```bash
npm install          # Install dependencies
npm run dev          # Dev server with hot reload (http://localhost:5173)
npm run build        # Production build -> dist/
npm run build:dev    # Build with development env
npm run build:staging  # Build with staging env
npm run build:prod   # Build with production env
npm run lint         # ESLint
npm run preview      # Preview production build locally
```

## 9. Production Build and Deployment

```bash
# Build with production API URL
VITE_API_URL=https://api.example.com npm run build
```

The `dist/` directory contains the production build. Deploy to any static hosting:

**CloudFront/S3:**
- **Origin:** S3 bucket containing `dist/` contents
- **Default root object:** `index.html`
- **Error pages:** Route 403/404 to `index.html` with 200 status (SPA client-side routing)
- **Cache:** Cache static assets (JS/CSS have content hashes), no-cache on `index.html`

**Other hosts:** Netlify, Vercel, or any static file server — just make sure all routes fall back to `index.html`.

## Troubleshooting

### Products page is empty

1. **Is the SAP Commerce server running?**
   ```bash
   curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15
   ```
   If this fails, start the MCP Server first.

2. **Has Solr been indexed?**
   Product search requires Solr indexing. In the MCP Server project:
   ```bash
   ./local/scripts/index-solr.sh
   ```

3. **Is the proxy working?**
   Check the Vite dev server terminal for proxy errors. The most common issue is SAP Commerce not running on port 9002.

### Login fails

- Verify the test user exists: `john.doe@thinkshop.com` / `1234`
- Verify the OAuth client works:
  ```bash
  curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
    -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
  ```
  If this returns an access token, the backend is fine — check the browser console for CORS or proxy errors.

### CORS errors in browser console

The Vite proxy should handle CORS in development. If you see CORS errors:
- Make sure you're accessing the UI at `http://localhost:5173` (not directly hitting the backend)
- Check that `vite.config.ts` proxy is configured correctly
- Restart the Vite dev server (`npm run dev`)

### Port 5173 already in use

Another process is using the port. Either kill it or change the Vite port in `vite.config.ts`:
```ts
export default defineConfig({
  server: {
    port: 3000,  // or any available port
  }
})
```

### TypeScript errors on first run

Run `npm install` to ensure all dependencies and type definitions are installed. If errors persist:
```bash
rm -rf node_modules package-lock.json
npm install
```
