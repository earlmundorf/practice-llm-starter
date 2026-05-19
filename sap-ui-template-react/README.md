# SAP UI Template (React)

A minimal React storefront scaffold for SAP Commerce via OCC REST APIs. Includes routing, auth context, cart context, and API service wiring — but no feature pages beyond the shell. Use this as a clean foundation for building your own decoupled UI.

## Claude Code Setup

This project exists to demonstrate a Claude Code setup for a decoupled React storefront on SAP Commerce before any features are built. It includes a `CLAUDE.md` with commands, code style guidelines, and project structure, plus a `.claude/skills/` covering React e-commerce patterns. The intent is to show how Claude Code should be configured at the start of a UI project — so the conventions and context are established before writing feature code.

## What's Included

- React 19, TypeScript, Tailwind CSS, Vite
- Auth context with OAuth2 flow wired to SAP Commerce
- Cart context with add/update/remove operations
- API service layer mapped to SAP Commerce OCC endpoints
- Full `CLAUDE.md` and `.claude/skills/` — minimal feature code

## Getting Started

Copy `.env.example` to `.env`, set `VITE_API_URL` to your SAP Commerce backend, then:

```bash
npm install
npm run dev
```
