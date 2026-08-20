# THINK-201 — Surface product reviews to the MCP agent

| Field | Value |
|---|---|
| **Project** | ThinkShop (THINK) |
| **Issue Type** | Story |
| **Priority** | Medium |
| **Components** | Backend · coremcp · MCP tools |
| **Labels** | mcp, reviews, agent, catalog |
| **Reporter** | Product / Conversational Commerce |

## Description

**As a** shopper talking to the ThinkShop assistant, **when** I ask about a product's
ratings or what other customers said, **I want** the assistant to return real review
content (rating, headline, comment), **so that** I can judge the product without leaving
the chat.

Today the assistant can't surface reviews. The `product_get` tool's schema lists a
`REVIEW` option and its description claims it returns "reviews," but in practice a caller
who asks for reviews gets nothing useful back. We need the MCP tool surface to return
actual review data for a product.

## Acceptance Criteria

1. An MCP caller can retrieve the reviews for a product by code and get back a list of
   reviews, each with at least **rating, headline, comment**, and the reviewer's display
   name/alias.
2. A product with **no reviews** returns an empty list (not an error).
3. The values match the reviews recorded for that product on the `electronics` site.
4. Sample review data exists for at least one demo product so the behavior is
   observable end-to-end.
5. No regression to existing `product_get` / `product_search` responses or other tools.

## Notes / Context

- SAP Commerce models customer reviews **out of the box** (the `customerreview`
  extension). Reuse the platform's existing review capability — do **not** build a new
  review model or service. Override/extend only in the custom extension.
- Keep the MCP payload lean and consistent with the other tools' response style.

## Out of Scope

- Writing/submitting reviews from the agent (read-only for this ticket).
- Review moderation / approval workflow changes.
- Storefront (Spartacus/React) rendering of reviews.
