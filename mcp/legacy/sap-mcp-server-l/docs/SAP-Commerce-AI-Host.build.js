// SAP-Commerce-AI-Host.pptx — 2-slide deck.
//   slide 1  MCP and UCP with SAP Commerce   (carries Jon's 7 edits)
//   slide 2  SAP CX AI Toolkit on Commerce Cloud
// One pptxgen instance per output file; palette and helpers shared so the two
// slides read as one family.
const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.3 x 7.5

// ---- shared palette ------------------------------------------------------
const BG        = "0F1729";
const CARD_CORE = "1C2740";
const CORE      = "93A7C6";
const WHITE     = "FFFFFF";
const MUTED     = "97A8C2";
const MONO_FG   = "DCE5F0";

const MCP = "02C39A", CARD_MCP = "132E33", LINE_MCP = "1F4A50";
const UCP = "F2A65A", CARD_UCP = "33261A", LINE_UCP = "553F26";
const TK  = "9B8CFA", CARD_TK  = "23204A", LINE_TK  = "45407F";

const SERIF = "Cambria";
const SANS  = "Calibri";
const MONO  = "Courier New";

// ---- shared geometry / helpers ------------------------------------------
const GUT_X = 0.5, GUT_W = 1.02;
const X = 1.66, FULL_W = 11.12;

function card(s, x, y, w, h, fill, line) {
  s.addShape(pres.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.06,
    fill: { color: fill }, line: { color: line, width: 1 },
  });
}

function tier(s, n, label, y, h) {
  s.addShape(pres.ShapeType.ellipse, {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31,
    fill: { color: BG }, line: { color: CORE, width: 1.25 },
  });
  s.addText(String(n), {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31, margin: 0,
    fontFace: SANS, fontSize: 11, bold: true, color: CORE,
    align: "center", valign: "middle",
  });
  s.addText(label, {
    x: GUT_X, y: y + h / 2 + 0.16, w: GUT_W, h: 0.34, margin: 0,
    fontFace: SANS, fontSize: 8.5, bold: true, color: MUTED, charSpacing: 0.8,
  });
}

function downArrow(s, cx, y, color) {
  s.addShape(pres.ShapeType.downArrow, {
    x: cx - 0.09, y, w: 0.18, h: 0.17,
    fill: { color }, line: { color, width: 0 },
  });
}

function title(s, text, w) {
  s.addText(text, {
    x: 0.5, y: 0.28, w, h: 0.52, margin: 0,
    fontFace: SERIF, fontSize: 33, bold: true, color: WHITE,
  });
}

function subtitle(s, text) {
  s.addText(text, {
    x: 0.5, y: 0.84, w: 11.6, h: 0.28, margin: 0,
    fontFace: SANS, fontSize: 12.5, color: MUTED,
  });
}

// =========================================================================
// SLIDE 1 — MCP and UCP with SAP Commerce
// =========================================================================
(function slide1() {
  const s = pres.addSlide();
  s.background = { color: BG };

  const L_X = 1.66, COL_W = 5.42, R_X = 7.36;
  const ROW = {
    a: { y: 1.66, h: 0.66 },
    b: { y: 2.54, h: 1.46 },
    c: { y: 4.22, h: 1.02 },
    d: { y: 5.46, h: 0.86 },
  };

  title(s, "MCP and UCP with SAP Commerce", 9.6);
  subtitle(s, "Two agent protocols, one commerce core — both are thin OCC v2 adapters over the same hybris facade layer.");

  function colHeader(x, label, sub, accent) {
    s.addShape(pres.ShapeType.roundRect, {
      x, y: 1.20, w: COL_W, h: 0.34, rectRadius: 0.05,
      fill: { color: accent }, line: { color: accent, width: 1 },
    });
    s.addText(
      [
        { text: label, options: { bold: true, fontSize: 13, color: BG, charSpacing: 1.5 } },
        { text: "   " + sub, options: { fontSize: 11.5, color: BG } },
      ],
      { x: x + 0.16, y: 1.20, w: COL_W - 0.32, h: 0.34, margin: 0, fontFace: SANS, valign: "middle" }
    );
  }

  function endpoints(x, y, w, h, rows, accent, footNote) {
    const t = [];
    rows.forEach((r, i) => {
      const last = i === rows.length - 1;
      t.push({ text: r[0].padEnd(7, " "), options: { fontFace: MONO, fontSize: 9, bold: true, color: accent } });
      t.push({ text: r[1], options: { fontFace: MONO, fontSize: 9, color: MONO_FG } });
      if (r[2]) t.push({ text: "  " + r[2], options: { fontFace: SANS, fontSize: 8.5, color: MUTED, italic: true } });
      if (!last) t[t.length - 1].options.breakLine = true;
    });
    s.addText(t, { x: x + 0.18, y: y + 0.13, w: w - 0.36, h: h - 0.44, margin: 0, valign: "top", paraSpaceAfter: 3 });
    s.addText(footNote, {
      x: x + 0.18, y: y + h - 0.32, w: w - 0.36, h: 0.24, margin: 0,
      fontFace: SANS, fontSize: 8.5, color: accent, italic: true, valign: "middle",
    });
  }

  function bulletBlock(x, y, w, h, lines, accent) {
    s.addText(
      lines.map((ln, i) => ({
        text: ln.b,
        options: {
          fontFace: SANS, fontSize: 10.5, color: ln.strong ? WHITE : MUTED, bold: !!ln.strong,
          bullet: { code: "25AA", color: accent, indent: 14 },
          breakLine: i !== lines.length - 1,
        },
      })),
      { x: x + 0.2, y: y + 0.12, w: w - 0.4, h: h - 0.24, margin: 0, valign: "top", paraSpaceAfter: 4 }
    );
  }

  colHeader(L_X, "MCP", "coremcp extension", MCP);
  colHeader(R_X, "UCP", "ucpcommerce extension", UCP);

  // tier 4 — agents
  tier(s, 4, "AGENTS", ROW.a.y, ROW.a.h);
  card(s, L_X, ROW.a.y, COL_W, ROW.a.h, CARD_MCP, LINE_MCP);
  card(s, R_X, ROW.a.y, COL_W, ROW.a.h, CARD_UCP, LINE_UCP);
  s.addText("Generally Available custom connector  ·  AI agent chat UI  ·  MCP chat clients", {
    x: L_X + 0.2, y: ROW.a.y, w: COL_W - 0.4, h: ROW.a.h, margin: 0,
    fontFace: SANS, fontSize: 10.5, color: WHITE, valign: "middle",
  });
  s.addText("Google UCP reference client  ·  official conformance suite  ·  any UCP agent", {
    x: R_X + 0.2, y: ROW.a.y, w: COL_W - 0.4, h: ROW.a.h, margin: 0,
    fontFace: SANS, fontSize: 10.5, color: WHITE, valign: "middle",
  });
  downArrow(s, L_X + COL_W / 2, ROW.a.y + ROW.a.h + 0.03, MCP);
  downArrow(s, R_X + COL_W / 2, ROW.a.y + ROW.a.h + 0.03, UCP);

  // tier 3 — endpoints
  tier(s, 3, "ENDPOINTS", ROW.b.y, ROW.b.h);
  card(s, L_X, ROW.b.y, COL_W, ROW.b.h, CARD_MCP, LINE_MCP);
  card(s, R_X, ROW.b.y, COL_W, ROW.b.h, CARD_UCP, LINE_UCP);
  endpoints(L_X, ROW.b.y, COL_W, ROW.b.h, [
    ["POST",   "/occ/v2/{site}/mcp", "JSON-RPC 2.0"],
    ["DELETE", "/occ/v2/{site}/mcp", "end session"],
    ["POST",   "/occ/v2/{site}/agent/chat", "+ /stream"],
    ["POST",   "/occ/v2/{site}/agent/visual-search"],
    ["GET",    "/occ/v2/{site}/info/search", "public"],
  ], MCP, "Streaming returns available – Agent Interaction");
  endpoints(R_X, ROW.b.y, COL_W, ROW.b.h, [
    ["GET",  "/occ/v2/{site}/.well-known/ucp", "anonymous"],
    ["POST", "/occ/v2/{site}/ucp/mcp", "stateless"],
    ["GET",  "/occ/v2/{site}/ucp/catalog/search"],
    ["POST", "/occ/v2/{site}/ucp/checkout-sessions"],
    ["GET",  "/occ/v2/{site}/ucp/orders/{id}"],
  ], UCP, "Discovery document advertises the REST + MCP base URLs. Idempotency-Key · UCP-Agent headers.");
  downArrow(s, L_X + COL_W / 2, ROW.b.y + ROW.b.h + 0.03, MCP);
  downArrow(s, R_X + COL_W / 2, ROW.b.y + ROW.b.h + 0.03, UCP);

  // tier 2 — services
  tier(s, 2, "SERVICES", ROW.c.y, ROW.c.h);
  card(s, L_X, ROW.c.y, COL_W, ROW.c.h, CARD_MCP, LINE_MCP);
  card(s, R_X, ROW.c.y, COL_W, ROW.c.h, CARD_UCP, LINE_UCP);
  bulletBlock(L_X, ROW.c.y, COL_W, ROW.c.h, [
    { b: "McpDispatcherService → Multiple tool handlers", strong: true },
    { b: "DB-persisted, cluster-safe session store + cart loader" },
    { b: "product · cart · voucher · order · checkout · customer" },
  ], MCP);
  bulletBlock(R_X, ROW.c.y, COL_W, ROW.c.h, [
    { b: "UcpCatalogService · UcpCheckoutService · UcpOrderService", strong: true },
    { b: "One service layer, two transports — no business logic in either" },
    { b: "Session store · idempotency records · money in minor units" },
  ], UCP);
  downArrow(s, L_X + COL_W / 2, ROW.c.y + ROW.c.h + 0.03, MCP);
  downArrow(s, R_X + COL_W / 2, ROW.c.y + ROW.c.h + 0.03, UCP);

  // tier 1 — platform
  tier(s, 1, "PLATFORM", ROW.d.y, ROW.d.h);
  card(s, L_X, ROW.d.y, FULL_W, ROW.d.h, CARD_CORE, "35486B");
  s.addText("SAP Commerce (hybris) service layer — shared, unmodified", {
    x: L_X + 0.22, y: ROW.d.y + 0.11, w: FULL_W - 0.44, h: 0.26, margin: 0,
    fontFace: SANS, fontSize: 11.5, bold: true, color: WHITE,
  });
  s.addText(
    "ProductSearchFacade · ProductFacade · CartFacade · VoucherFacade · CheckoutFacade · OrderFacade · CustomerFacade        Search · FlexibleSearch · OAuth2 authorizationserver",
    { x: L_X + 0.22, y: ROW.d.y + 0.40, w: FULL_W - 0.44, h: 0.46, margin: 0,
      fontFace: SANS, fontSize: 10, color: CORE, valign: "top" }
  );

  s.addNotes(
    "Read the diagram bottom-up. Tier 1 is stock SAP Commerce: neither protocol extension changes it. " +
    "Tier 2 is where each protocol keeps its own capability services -- coremcp exposes its commerce tools through a " +
    "JSON-RPC dispatcher; ucpcommerce keeps its services deliberately binding-agnostic, which is why " +
    "adding the REST transport required zero service-layer changes. " +
    "Tier 3 is the visible difference. UCP publishes a discovery document: GET /occ/v2/{site}/.well-known/ucp is " +
    "anonymous and returns the official registry shape, and the endpoints it advertises are what a discovery-driven " +
    "client (the official conformance suite) actually routes through. That is why the local dev proxy rewrites those " +
    "advertised URLs to itself. MCP instead establishes a session through the initialize handshake. " +
    "In production the discovery document must be public at the host root via an edge rewrite. " +
    "Note the two UCP transports are identical tools/routes over one service layer, and the MCP transport is optional -- " +
    "dropping it from the advertised service list still leaves a conformant profile."
  );
})();

// =========================================================================
// SLIDE 2 — SAP CX AI Toolkit on Commerce Cloud
// LIGHT drop-in diagram for the Capgemini RFP template.
// Middle box = where the toolkit plugs into existing operator surfaces
// (Joule AI Action Bar + Backoffice). The Staged/sync publish path is scoped
// to CONTENT GENERATION only -- it is not how runtime agent responses work.
(function slide2() {
  const s = pres.addSlide();

  const L_BG   = "FFFFFF";
  const L_CARD = "F2F0FD", L_LINE = "C7BDF4";
  const L_MID  = "F7F6FE", L_MLINE = "D6CFF7";
  const L_CORE = "EFF3F8", L_CLINE = "C2CEDF";
  const L_TK   = "6A55C8";
  const INK    = "1B2233";
  const GREY   = "5A6478";

  s.background = { color: L_BG };

  const B1 = { y: 0.58, h: 1.90 };
  const B2 = { y: 2.84, h: 1.62 };
  const B3 = { y: 4.82, h: 1.52 };

  function lcard(x, y, w, h, fill, line) {
    s.addShape(pres.ShapeType.roundRect, {
      x, y, w, h, rectRadius: 0.06,
      fill: { color: fill }, line: { color: line, width: 1 },
    });
  }
  function ltier(n, label, y, h) {
    s.addShape(pres.ShapeType.ellipse, {
      x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31,
      fill: { color: L_BG }, line: { color: L_TK, width: 1.25 },
    });
    s.addText(String(n), {
      x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31, margin: 0,
      fontFace: SANS, fontSize: 11, bold: true, color: L_TK,
      align: "center", valign: "middle",
    });
    s.addText(label, {
      x: GUT_X, y: y + h / 2 + 0.16, w: GUT_W, h: 0.34, margin: 0,
      fontFace: SANS, fontSize: 8.5, bold: true, color: GREY, charSpacing: 0.8,
    });
  }
  function ldown(cx, y) {
    s.addShape(pres.ShapeType.downArrow, {
      x: cx - 0.09, y, w: 0.18, h: 0.17,
      fill: { color: L_TK }, line: { color: L_TK, width: 0 },
    });
  }

  // ---- 3. the toolkit ----------------------------------------------------
  ltier(3, "TOOLKIT", B1.y, B1.h);
  lcard(X, B1.y, FULL_W, B1.h, L_CARD, L_LINE);
  s.addText(
    [
      { text: "SAP CX AI Toolkit", options: { fontSize: 15, bold: true, color: INK } },
      { text: "     a separate SAP-hosted tenant, mapped to your Commerce Cloud tenant — no code deployed into the platform",
        options: { fontSize: 10.5, color: L_TK } },
    ],
    { x: X + 0.28, y: B1.y + 0.16, w: FULL_W - 0.56, h: 0.34, margin: 0, fontFace: SANS, valign: "middle" }
  );
  const AREAS = [
    ["Product Enrichment",
     "Generate descriptions, attributes and images from your own catalog data, with tone and length governed by admin-managed templates."],
    ["Content Management",
     "Blog posts, social copy and product banner text produced at volume, grounded in the same catalog and kept on brand."],
    ["Storefront Agentic AI",
     "Conversational shopping and visual search over the live catalog, covering cart, order status, pricing and stock lookups."],
  ];
  const AW = (FULL_W - 0.56 - 2 * 0.34) / 3;
  AREAS.forEach(([n, d], i) => {
    const ax = X + 0.28 + i * (AW + 0.34);
    s.addText(n, {
      x: ax, y: B1.y + 0.68, w: AW, h: 0.28, margin: 0,
      fontFace: SANS, fontSize: 12, bold: true, color: L_TK, valign: "top",
    });
    s.addText(d, {
      x: ax, y: B1.y + 1.00, w: AW, h: 0.78, margin: 0,
      fontFace: SANS, fontSize: 10.5, color: GREY, valign: "top",
    });
  });
  ldown(X + FULL_W / 2, B1.y + B1.h + 0.08);

  // ---- 2. instrumentation into existing surfaces -------------------------
  ltier(2, "INSTRUMENTED", B2.y, B2.h);
  lcard(X, B2.y, FULL_W, B2.h, L_MID, L_MLINE);
  s.addText("HOW IT PLUGS INTO EXISTING OPERATIONS", {
    x: X + 0.28, y: B2.y + 0.14, w: FULL_W - 0.56, h: 0.26, margin: 0,
    fontFace: SANS, fontSize: 9, bold: true, color: L_TK, charSpacing: 1,
  });
  const PLUGS = [
    ["Joule AI Action Bar",
     "Toolkit actions surface through SAP's copilot, so business users trigger them from where they already work — browser, mobile or Joule."],
    ["Backoffice",
     "Open in Commerce deep-links to the product in Backoffice. Review, edit and catalog publishing all run on existing Backoffice tooling and permissions."],
  ];
  const PW = (FULL_W - 0.56 - 0.44) / 2;
  PLUGS.forEach(([n, d], i) => {
    const px = X + 0.28 + i * (PW + 0.44);
    s.addText(n, {
      x: px, y: B2.y + 0.44, w: PW, h: 0.28, margin: 0,
      fontFace: SANS, fontSize: 12, bold: true, color: L_TK, valign: "top",
    });
    s.addText(d, {
      x: px, y: B2.y + 0.76, w: PW, h: 0.62, margin: 0,
      fontFace: SANS, fontSize: 10.5, color: GREY, valign: "top",
    });
  });
  ldown(X + FULL_W / 2, B2.y + B2.h + 0.08);

  // ---- 1. CCv2 -----------------------------------------------------------
  ltier(1, "CCv2", B3.y, B3.h);
  lcard(X, B3.y, FULL_W, B3.h, L_CORE, L_CLINE);
  s.addText("SAP Commerce Cloud (CCv2) — unchanged", {
    x: X + 0.28, y: B3.y + 0.18, w: FULL_W - 0.56, h: 0.32, margin: 0,
    fontFace: SANS, fontSize: 15, bold: true, color: INK, valign: "middle",
  });
  s.addText(
    [
      { text: "Product catalog  ·  pricing  ·  stock  ·  cart  ·  orders  ·  customer", options: { color: INK, bold: true, breakLine: true } },
      { text: "Commerce remains the system of record. Every toolkit capability resolves to a Commerce service that already exists — no new extension, no custom endpoint, and no change to the storefront itself.",
        options: { color: GREY } },
    ],
    { x: X + 0.28, y: B3.y + 0.60, w: FULL_W - 0.56, h: 0.88, margin: 0,
      fontFace: SANS, fontSize: 10.5, valign: "top", paraSpaceAfter: 5 }
  );

  s.addNotes(
    "Three boxes: what the toolkit does, where it plugs into surfaces your operators already use, and the Commerce platform underneath, unchanged. " +
    "IMPORTANT SCOPING -- the Staged catalog and Backoffice sync path applies to CONTENT GENERATION (product descriptions, attributes, marketing copy). " +
    "It is NOT how the Storefront Agentic AI works: an agent answers a shopper in real time by reading the live catalog, cart, order and stock services. " +
    "Do not describe the agent as publishing through Staged. " +
    "CONTENT PUBLISH PATH, if asked -- single product: find it, generate, publish, then Open in Commerce goes straight into Backoffice to validate and " +
    "sync. Bulk: select products, generate, publish to Stage, then run Backoffice Multithreaded Synchronization (catalog Staged to Online). Status shows " +
    "under Processes and takes a few minutes. Bulk generation is capped at ten products per run and another batch cannot start until the previous finishes. " +
    "GOVERNANCE -- that sync moves EVERY staged change, not only what was just generated, so whoever runs it can publish unrelated staged work. It needs " +
    "Backoffice access plus permission to run synchronisation jobs; recommend restricting that permission. " +
    "GENERATION CONTROLS -- admin-managed templates with a playground for testing against a real product, custom instructions, tone, length, and which " +
    "product and classification attributes feed the model. " +
    "VERIFY BEFORE COMMITTING -- the Joule AI Action Bar claim rests on thin evidence (a single mention in the SAP Learning material) and has not been " +
    "confirmed for Commerce specifically. Entitlement also varies by tenant: Product Images, Visual Search, AI Tool Builder and AI Agents can each " +
    "require additional setup or licence, so confirm what is entitled before putting a capability in scope. " +
    "TRUSTED AI -- secure retrieval on user credentials, RAG grounding in SAP CX data, obfuscation and bias filtering, zero data retention, audit logs " +
    "with trace IDs, human in the loop."
  );
})();

pres.writeFile({ fileName: "SAP-Commerce-AI-Host.pptx" }).then((f) => console.log("wrote " + f));
