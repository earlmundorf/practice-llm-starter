# Storefront QRSPI — Developer Walkthrough

A day-in-the-life run, end to end, in this repo. Example ticket: **UI-101 — "Add an AI
shopping assistant chat widget"** (the coremcp backend's `/agent/chat` endpoints exist;
this storefront has no client for them yet — see the `commerce-storefront` skill for
the contracts).

---

## 0. Already installed

This repo ships the skill, the `/cq` commands, and a seeded
`working-docs/config.json` (npm verbs + React research layers). Verify: type `/cq` in
Claude Code — autocomplete shows `0_go` … `7_validate`. `jira.mode` is `manual`, so you
paste ticket text when asked and outbound updates land paste-ready in
`working-docs/<KEY>/jira-updates.md`.

---

## 1. Start — one command (~3 min, you skim)

```
> /cq:go UI-101
```

Claude asks you to paste the ticket (manual mode), then recommends a tier:

> "New feature touching API client, state, components, and routing, with real design
> choices (placement, streaming, state location) — I recommend **full**.
> (trivial / simple / full / comprehensive)"

You confirm `full`. Stages 1+2 auto-chain. Two files appear:

**`working-docs/UI-101/ticket.md`** (excerpt)
> **Problem:** Shoppers should converse with the ThinkShop AI assistant from any page —
> ask questions, get product suggestions, manage their cart — without leaving where
> they are.
> **ASSUMPTION-1:** The backend agent endpoints are reachable through the existing
> Vite proxy with the user's Bearer token.
> **ASSUMPTION-2:** Streaming is desirable but optional (backend documents a JSON
> fallback).
> **Draft success criteria (from AC):** widget opens from any page; replies render
> incrementally when streaming; suggested follow-ups are tappable; product/order chips
> navigate; cart badge updates after agent cart actions.

**`working-docs/UI-101/questions.md`** (excerpt — neutral, no solutioning)
> Q1. How do components currently call the backend, and how are auth headers and 401s
> handled? (file:line)
> Q2. What cross-component signals exist (events, contexts) and who consumes them?
> Q3. Is there any SSE/streaming handling anywhere in the codebase today?
> Q4. How are overlays/modals/toasts rendered and layered (z-index, portals)?
> Q5. How does CartContext learn about cart changes made outside its own calls?
> Q6. What does the backend contract for /agent/chat and /agent/chat/stream specify?
> (reference docs path)

## 2. Research — blind, layered (0 min of yours)

Stage 2 reads `questions.md` only (never the ticket), dispatches one subagent per
layer from `researchLayers` in config (routing-pages, components, state, api-types,
build-config), and writes `research.md` — facts with `file:line`:

> A1. All calls go through `apiFetch<T>` in `src/services/api.ts:29` — Bearer from
> localStorage, 401 → `auth.logout()` + `authExpired` event (api.ts:41-45).
> A3. No SSE handling exists anywhere in src/. UNANSWERED beyond that — nothing to find.
> A5. CartContext subscribes to the `cartUpdated` window event (CartContext.tsx:…).
> A6. Contract documented in
> ../mcp/legacy/sap-mcp-server-l/.../coremcp/docs/reference/endpoints.md — events
> text/tool/done/error; JSON fallback signaled by Content-Type; 429 per-user limit;
> response carries reply (with SUGGESTIONS line), messages, cartCode, action, entityRefs.

## 3. Design — DEV GATE 1 (~10 min, the leverage point)

```
> /cq:3_design working-docs/UI-101/
```

Claude must ask before writing anything:

> **Q1 Placement** — (A) floating launcher button + slide-up panel on every page via
> `Layout.tsx`, (B) dedicated `/assistant` route, (C) both. Recommend A: matches
> "from any page", no routing changes.
> **Q2 Streaming** — (A) SSE reader with documented JSON fallback (branch on
> Content-Type), (B) non-streaming only first. Recommend A; the fallback makes it safe.
> **Q3 Conversation state** — (A) new ChatContext (history, open/closed, cartCode
> echo), (B) component-local state. Recommend A: survives panel close, syncs cartCode
> to CartContext.
> **ASSUMPTION-1 confirmed** (vite proxy covers /occ; token attaches in apiFetch).
> **ASSUMPTION-2 confirmed** by the backend's documented fallback.

You answer (say, A/A/A). `design.md` (~200 lines) lands with current state, desired
end state, the three decisions, success criteria split **Automated** (TYPECHECK, LINT,
BUILD green) / **Manual** (dev-server script: open widget on /products, send "do you
sell laptops?", see streamed reply + suggestion buttons + product chips; agent adds to
cart → header badge updates; kill streaming flag on backend → JSON fallback still
works; 429 shows friendly retry). You approve.

## 4. Structure — DEV GATE 2 (~5 min)

Vertical slices, each with a checkpoint (verbs from config):

> **S1** `api.ts` + types: `agentChat()`, `agentChatStream()` (reader + fallback),
> `getAgentCapabilities()`; new types `AgentChatResponse`, `EntityRef`.
> ✓ TYPECHECK + LINT + BUILD
> **S2** ChatContext + launcher + panel shell, non-streaming send/receive, SUGGESTIONS
> parsed off the reply into buttons. ✓ TYPECHECK + LINT + BUILD + manual smoke
> **S3** Streaming: incremental text, "Looking up…" on tool events, Content-Type
> fallback branch, AbortController on close/navigation. ✓ same + manual stream check
> **S4** entityRefs chips (product → product page, order → orders), cartCode → dispatch
> `cartUpdated`. ✓ same + manual chip/badge check
> **S5** Polish (dark mode, a11y, empty/error/429 states) + flow doc
> `docs/agent-chat-widget/{context,components,diagram}.md`. ✓ full verification

You approve. (UNIT_TEST is `MANUAL:` in this repo's config until Vitest lands — the
checkpoints lean on typecheck/lint/build plus scripted manual checks, and say so.)

## 5 → 6. Plan, then implement (~2 min spot-check, then watch or pair)

```
> /cq:5_plan working-docs/UI-101/      # checkboxed tasks, commands resolved from config
> /cq:6_implement working-docs/UI-101/ mode=claude
```

`mode=claude`: one fresh subagent per slice, verify per task, **never proceeds on
red**, one commit per slice, checkboxes updated (resume-safe). `mode=dev`: you write
the code, Claude pairs and runs the checkpoints.

## 7. Validate & ship — DEV GATE 3 (~10 min)

```
> /cq:7_validate working-docs/UI-101/
```

Re-runs every automated criterion verbatim; lists the manual script with exact
routes/steps for you to click through; then the **ownership gate**: full diff summary
and the question — *"Have you read this diff and do you own it?"* Only on your yes:
PR grounded in design.md, and the Jira comment + status transition written to
`working-docs/UI-101/jira-updates.md` for you to paste.

---

## The other tiers, in one line each

- **trivial** — `/cq:go "copy change on the empty-cart message" trivial` → fix,
  TYPECHECK+LINT+BUILD, diff, own it. Done in minutes.
- **simple** — `/cq:go UI-102 simple` (known bug: badge doesn't reset on logout) →
  one-page `brief.md` (you gate the assumptions) → implement → validate-lite.
- **comprehensive** — `/cq:go UI-200 comprehensive` (auth/routing overhaul) →
  everything above + worktree isolation + full verification per slice + design/structure
  shared for team review before code.

## Combined CCv2 repos

If your storefront lives inside the backend repo (`js-storefront/` beside
`core-customize/`), this variant is not installed there — `commerce-qrspi` runs the
whole flow with a frontend verb namespace (`FE_BUILD`, `FE_TEST`, …) and slices that
cut vertically across the stack. See that skill's "Combined repos" section.
