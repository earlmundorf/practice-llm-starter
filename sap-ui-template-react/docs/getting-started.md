# Getting Started — Local Development

This guide walks you through setting up the SAP Commerce React UI for local development.

## Prerequisites

### SAP Commerce Backend (required)

The UI connects to SAP Commerce via OCC REST APIs. **You must have the SAP Commerce server running locally before the UI will work.**

**Verify the backend is running:**
```bash
curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15
```

If you get a JSON response with product data, the backend is ready.

### Node.js 20+

```bash
node --version    # Must be 20+
```

**Install via Homebrew (macOS):**
```bash
brew install node@20
```

## 1. Clone and Install

```bash
git clone <this-repo-url>
cd SAP-UI-Template
npm install
```

## 2. Configure Environment

For standard local development, **no configuration is needed**. The defaults in `.env.development` point to the local SAP Commerce server.

If you need to override anything:
```bash
cp .env.example .env
```

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `/occ/v2/electronics` | OCC API base path (proxied to SAP Commerce in dev) |

## 3. Start the Dev Server

```bash
npm run dev
```

The UI starts at **http://localhost:5173** with hot module replacement (HMR).

## 4. Verify Everything Works

### Browse Products

Navigate to the Products page. You should see electronics products with prices and descriptions.

If the product list is empty:
1. Is the SAP Commerce server running?
2. Has Solr been indexed? (`./local/scripts/index-solr.sh` in the backend project)

### Log In

| Email | Password |
|-------|----------|
| john.doe@thinkshop.com | 1234 |
| jane.smith@thinkshop.com | 1234 |
| bob.wilson@thinkshop.com | 1234 |

**OAuth client:** `trusted_client` / `secret`

### Add to Cart and Checkout

1. Browse products and add one to your cart
2. Open the cart and proceed to checkout
3. Complete the checkout flow (address, delivery mode, payment, place order)
4. View the order in Order History

## 5. How the Proxy Works

```
Browser :5173  -->  Vite Dev Server  -->  SAP Commerce :9002 (HTTPS)
  /occ/v2/*           proxy                /occ/v2/*
  /authorizationserver/*                   /authorizationserver/*
```

The proxy (configured in `vite.config.ts`):
- Targets `https://localhost:9002` (SAP Commerce HTTPS port)
- Sets `secure: false` for the self-signed certificate
- Strips the `Origin` header (SAP Commerce CORS filter rejects unknown origins in dev)

**In production,** there is no proxy. The built app makes direct requests to the API URL specified by `VITE_API_URL` at build time.

## 6. Available Commands

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

## 7. Production Deployment

```bash
VITE_API_URL=https://api.example.com npm run build
```

Deploy the `dist/` directory to any static hosting:

**CloudFront/S3:**
- Origin: S3 bucket containing `dist/` contents
- Default root object: `index.html`
- Error pages: Route 403/404 to `index.html` with 200 status (SPA client-side routing)
- Cache: Cache static assets (JS/CSS have content hashes), no-cache on `index.html`

## Troubleshooting

### Products page is empty
1. Is SAP Commerce running? `curl -sk https://localhost:9002/occ/v2/electronics/products/LAPTOP_PRO_15`
2. Has Solr been indexed?
3. Check Vite terminal for proxy errors

### Login fails
```bash
curl -sk -X POST https://localhost:9002/authorizationserver/oauth/token \
  -d 'client_id=trusted_client&client_secret=secret&grant_type=password&username=john.doe@thinkshop.com&password=1234'
```

### CORS errors
Make sure you're accessing at `http://localhost:5173`, not hitting the backend directly. Restart Vite if needed.

### Port 5173 in use
Change in `vite.config.ts`: `server: { port: 3000 }`
