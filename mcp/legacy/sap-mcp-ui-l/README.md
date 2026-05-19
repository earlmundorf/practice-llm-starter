# ThinkShop UI

A React storefront for SAP Commerce (Hybris) via OCC REST APIs. Built as a standalone SPA deployable to CloudFront/S3 or any static hosting provider.

## Features

- Product browsing with Solr-powered search, facets, and sorting
- User authentication via OAuth2
- Shopping cart with add/update/remove
- Full checkout flow (address, delivery, payment, order placement)
- Order history and order detail views
- Responsive design with Tailwind CSS

## Tech Stack

- **React 19** with TypeScript
- **Tailwind CSS 4** for styling
- **Vite 7** for build and dev server
- **React Router 7** for client-side routing

## Quick Start

```bash
# Install dependencies
npm install

# Start dev server (proxies to SAP Commerce on localhost:9002)
npm run dev
```

The UI starts at http://localhost:5173 and proxies API requests to SAP Commerce.

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `https://localhost:9002` | SAP Commerce backend URL |

Copy `.env.example` to `.env` to configure:

```bash
cp .env.example .env
```

## Production Deployment (CloudFront/S3)

```bash
# Build with production API URL
VITE_API_URL=https://api.example.com npm run build
```

Deploy the `dist/` directory to an S3 bucket. Configure CloudFront:
- **Origin:** S3 bucket
- **Default root object:** `index.html`
- **Error pages:** Route 403/404 to `index.html` with 200 status (SPA fallback)
- **Cache:** Cache static assets, no-cache on `index.html`

## Project Structure

```
ThinkShop-UI/
├── src/
│   ├── components/        # Reusable UI components
│   ├── pages/             # Page-level components
│   ├── services/api.ts    # OCC API client with OAuth2
│   ├── contexts/          # React Context providers (auth, cart)
│   └── types/             # TypeScript type definitions
├── docs/                  # Architecture & planning docs
├── sample/                # Read-only reference app
├── public/                # Static assets
├── vite.config.ts         # Vite config with API proxy
├── .env.example           # Environment variable template
└── .claude/skills/        # Claude Code AI assistant skills
```

## Test Users

These accounts are created by SAP Commerce sample data:

| Email | Password |
|-------|----------|
| john.doe@thinkshop.com | 1234 |
| jane.smith@thinkshop.com | 1234 |
| bob.wilson@thinkshop.com | 1234 |

## OAuth Client

| Client ID | Client Secret |
|-----------|--------------|
| trusted_client | secret |

## Commands

```bash
npm install      # Install dependencies
npm run dev      # Dev server with hot reload (http://localhost:5173)
npm run build    # Production build → dist/
npm run lint     # ESLint
```

## Claude Code Skills

This project includes Claude Code skills in `.claude/skills/` for SAP Commerce domain expertise, useful when working with the OCC API integration layer.
