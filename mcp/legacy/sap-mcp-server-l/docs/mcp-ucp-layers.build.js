const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.3 x 7.5

// ---- palette -------------------------------------------------------------
const BG        = "0F1729";
const CARD_MCP  = "132E33";
const CARD_UCP  = "33261A";
const CARD_CORE = "1C2740";
const MCP       = "02C39A";
const UCP       = "F2A65A";
const CORE      = "93A7C6";
const WHITE     = "FFFFFF";
const MUTED     = "97A8C2";
const MONO_FG   = "DCE5F0";

const SERIF = "Cambria";
const SANS  = "Calibri";
const MONO  = "Courier New";

// ---- geometry ------------------------------------------------------------
const GUT_X = 0.5,  GUT_W = 1.02;
const L_X   = 1.66, COL_W = 5.42;
const R_X   = 7.36;
const FULL_W = 11.12;

const ROW = {
  a: { y: 1.66, h: 0.66 },
  b: { y: 2.54, h: 1.46 },
  c: { y: 4.22, h: 1.02 },
  d: { y: 5.46, h: 0.86 },
};

const slide = pres.addSlide();
slide.background = { color: BG };

// ---- title ---------------------------------------------------------------
slide.addText("MCP and UCP on SAP Commerce", {
  x: 0.5, y: 0.28, w: 9.6, h: 0.52, margin: 0,
  fontFace: SERIF, fontSize: 33, bold: true, color: WHITE,
});
slide.addText(
  "Two agent protocols, one commerce core — both are thin OCC v2 adapters over the same hybris facade layer.",
  {
    x: 0.5, y: 0.84, w: 11.4, h: 0.28, margin: 0,
    fontFace: SANS, fontSize: 12.5, color: MUTED,
  }
);
slide.addText("ThinkShop · site electronics · UCP 2026-04-08", {
  x: 9.5, y: 0.32, w: 3.3, h: 0.36, margin: 0,
  fontFace: SANS, fontSize: 10.5, color: MUTED, align: "right", italic: true,
});

// ---- helpers -------------------------------------------------------------
function card(x, y, w, h, fill, line) {
  slide.addShape(pres.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.06,
    fill: { color: fill },
    line: { color: line, width: 1 },
  });
}

function colHeader(x, label, sub, accent) {
  slide.addShape(pres.ShapeType.roundRect, {
    x, y: 1.20, w: COL_W, h: 0.34, rectRadius: 0.05,
    fill: { color: accent },
    line: { color: accent, width: 1 },
  });
  slide.addText(
    [
      { text: label, options: { bold: true, fontSize: 13, color: BG, charSpacing: 1.5 } },
      { text: "   " + sub, options: { fontSize: 11.5, color: BG } },
    ],
    { x: x + 0.16, y: 1.20, w: COL_W - 0.32, h: 0.34, margin: 0, fontFace: SANS, valign: "middle" }
  );
}

function tier(n, label, y, h, accent) {
  slide.addShape(pres.ShapeType.ellipse, {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31,
    fill: { color: BG }, line: { color: accent, width: 1.25 },
  });
  slide.addText(String(n), {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31, margin: 0,
    fontFace: SANS, fontSize: 11, bold: true, color: accent,
    align: "center", valign: "middle",
  });
  slide.addText(label, {
    x: GUT_X, y: y + h / 2 + 0.16, w: GUT_W, h: 0.34, margin: 0,
    fontFace: SANS, fontSize: 8.5, bold: true, color: MUTED,
    align: "left", valign: "top", charSpacing: 0.8,
  });
}

function downArrow(cx, y, color) {
  slide.addShape(pres.ShapeType.downArrow, {
    x: cx - 0.09, y, w: 0.18, h: 0.17,
    fill: { color }, line: { color, width: 0 },
  });
}

// endpoint block: array of [method, path, note]
function endpoints(x, y, w, h, rows, accent, footNote) {
  const t = [];
  rows.forEach((r, i) => {
    const last = i === rows.length - 1;
    t.push({ text: r[0].padEnd(7, " "), options: { fontFace: MONO, fontSize: 9, bold: true, color: accent } });
    t.push({ text: r[1], options: { fontFace: MONO, fontSize: 9, color: MONO_FG } });
    if (r[2]) t.push({ text: "  " + r[2], options: { fontFace: SANS, fontSize: 8.5, color: MUTED, italic: true } });
    if (!last) t[t.length - 1].options.breakLine = true;
  });
  slide.addText(t, {
    x: x + 0.18, y: y + 0.13, w: w - 0.36, h: h - 0.44,
    margin: 0, valign: "top", paraSpaceAfter: 3,
  });
  slide.addText(footNote, {
    x: x + 0.18, y: y + h - 0.32, w: w - 0.36, h: 0.24, margin: 0,
    fontFace: SANS, fontSize: 8.5, color: accent, italic: true, valign: "middle",
  });
}

function bulletBlock(x, y, w, h, lines, accent) {
  const t = [];
  lines.forEach((ln, i) => {
    t.push({
      text: ln.b,
      options: {
        fontFace: SANS, fontSize: 10.5, color: ln.strong ? WHITE : MUTED,
        bold: !!ln.strong, bullet: { code: "25AA", color: accent, indent: 14 },
        breakLine: i !== lines.length - 1,
      },
    });
  });
  slide.addText(t, {
    x: x + 0.2, y: y + 0.12, w: w - 0.4, h: h - 0.24,
    margin: 0, valign: "top", paraSpaceAfter: 4,
  });
}

// ---- column headers ------------------------------------------------------
colHeader(L_X, "MCP", "coremcp extension", MCP);
colHeader(R_X, "UCP", "ucpcommerce extension", UCP);

// ---- tier 4 — agents -----------------------------------------------------
tier(4, "AGENTS", ROW.a.y, ROW.a.h, CORE);
card(L_X, ROW.a.y, COL_W, ROW.a.h, CARD_MCP, "1F4A50");
card(R_X, ROW.a.y, COL_W, ROW.a.h, CARD_UCP, "553F26");
slide.addText("claude.ai custom connector  ·  AI agent chat UI  ·  MCP chat clients", {
  x: L_X + 0.2, y: ROW.a.y, w: COL_W - 0.4, h: ROW.a.h, margin: 0,
  fontFace: SANS, fontSize: 10.5, color: WHITE, valign: "middle",
});
slide.addText("Google UCP reference client  ·  official conformance suite  ·  any UCP agent", {
  x: R_X + 0.2, y: ROW.a.y, w: COL_W - 0.4, h: ROW.a.h, margin: 0,
  fontFace: SANS, fontSize: 10.5, color: WHITE, valign: "middle",
});

downArrow(L_X + COL_W / 2, ROW.a.y + ROW.a.h + 0.03, MCP);
downArrow(R_X + COL_W / 2, ROW.a.y + ROW.a.h + 0.03, UCP);

// ---- tier 3 — endpoints --------------------------------------------------
tier(3, "ENDPOINTS", ROW.b.y, ROW.b.h, CORE);
card(L_X, ROW.b.y, COL_W, ROW.b.h, CARD_MCP, "1F4A50");
card(R_X, ROW.b.y, COL_W, ROW.b.h, CARD_UCP, "553F26");

endpoints(L_X, ROW.b.y, COL_W, ROW.b.h, [
  ["POST",   "/occ/v2/{site}/mcp", "JSON-RPC 2.0"],
  ["DELETE", "/occ/v2/{site}/mcp", "end session"],
  ["POST",   "/occ/v2/{site}/agent/chat", "+ /stream"],
  ["POST",   "/occ/v2/{site}/agent/visual-search"],
  ["GET",    "/occ/v2/{site}/info/search", "public"],
], MCP, "No .well-known — the client discovers tools via the initialize handshake (MCP-Session-Id).");

endpoints(R_X, ROW.b.y, COL_W, ROW.b.h, [
  ["GET",  "/occ/v2/{site}/.well-known/ucp", "anonymous"],
  ["POST", "/occ/v2/{site}/ucp/mcp", "stateless"],
  ["GET",  "/occ/v2/{site}/ucp/catalog/search"],
  ["POST", "/occ/v2/{site}/ucp/checkout-sessions"],
  ["GET",  "/occ/v2/{site}/ucp/orders/{id}"],
], UCP, "Discovery document advertises the REST + MCP base URLs. Idempotency-Key · UCP-Agent headers.");

downArrow(L_X + COL_W / 2, ROW.b.y + ROW.b.h + 0.03, MCP);
downArrow(R_X + COL_W / 2, ROW.b.y + ROW.b.h + 0.03, UCP);

// ---- tier 2 — services --------------------------------------------------
tier(2, "SERVICES", ROW.c.y, ROW.c.h, CORE);
card(L_X, ROW.c.y, COL_W, ROW.c.h, CARD_MCP, "1F4A50");
card(R_X, ROW.c.y, COL_W, ROW.c.h, CARD_UCP, "553F26");

bulletBlock(L_X, ROW.c.y, COL_W, ROW.c.h, [
  { b: "McpDispatcherService → 19 tool handlers", strong: true },
  { b: "DB-persisted, cluster-safe session store + cart loader" },
  { b: "product · cart · voucher · order · checkout · customer" },
], MCP);

bulletBlock(R_X, ROW.c.y, COL_W, ROW.c.h, [
  { b: "UcpCatalogService · UcpCheckoutService · UcpOrderService", strong: true },
  { b: "Binding-agnostic (R12) — REST and MCP are thin adapters" },
  { b: "Session store · idempotency records · money in minor units" },
], UCP);

downArrow(L_X + COL_W / 2, ROW.c.y + ROW.c.h + 0.03, MCP);
downArrow(R_X + COL_W / 2, ROW.c.y + ROW.c.h + 0.03, UCP);

// ---- tier 1 — platform (shared) -----------------------------------------
tier(1, "PLATFORM", ROW.d.y, ROW.d.h, CORE);
card(L_X, ROW.d.y, FULL_W, ROW.d.h, CARD_CORE, "35486B");
slide.addText("SAP Commerce (hybris) service layer — shared, unmodified", {
  x: L_X + 0.22, y: ROW.d.y + 0.11, w: FULL_W - 0.44, h: 0.26, margin: 0,
  fontFace: SANS, fontSize: 11.5, bold: true, color: WHITE,
});
slide.addText(
  "ProductSearchFacade · ProductFacade · CartFacade · VoucherFacade · CheckoutFacade · OrderFacade · CustomerFacade        Solr · FlexibleSearch · OAuth2 authorizationserver",
  {
    x: L_X + 0.22, y: ROW.d.y + 0.40, w: FULL_W - 0.44, h: 0.46, margin: 0,
    fontFace: SANS, fontSize: 10, color: CORE, valign: "top",
  }
);

// ---- footer note --------------------------------------------------------
slide.addText(
  [
    { text: "ucpcommerce depends on coremcp ", options: { bold: true, color: WHITE } },
    { text: "(ADR 0001, MCP-first) \u2014 ", options: { color: MUTED } },
    { text: "com.thinkshop.*", options: { fontFace: MONO, color: UCP } },
    { text: " capabilities wrap coremcp services directly. Both stacks: one OAuth2 bearer (", options: { color: MUTED } },
    { text: "ROLE_CUSTOMERGROUP / ROLE_TRUSTED_CLIENT", options: { fontFace: MONO, color: MONO_FG } },
    { text: ").", options: { color: MUTED } },
  ],
  { x: 0.5, y: 6.62, w: 12.3, h: 0.34, margin: 0, fontFace: SANS, fontSize: 9.5, valign: "middle" }
);

slide.addNotes(
  "Read the diagram bottom-up. Tier 1 is stock SAP Commerce: neither protocol extension changes it. " +
  "Tier 2 is where each protocol keeps its own capability services -- coremcp exposes 19 commerce tools through a " +
  "JSON-RPC dispatcher; ucpcommerce keeps its services deliberately binding-agnostic (design rule R12), which is why " +
  "adding the REST transport in Phase 7 required zero service-layer changes. " +
  "Tier 3 is the visible difference. MCP has no discovery document -- a client POSTs an initialize call to " +
  "/occ/v2/{site}/mcp and gets a session id back. UCP does have one: GET /occ/v2/{site}/.well-known/ucp is " +
  "anonymous and returns the official registry shape, and the endpoints it advertises are what a discovery-driven " +
  "client (the official conformance suite) actually routes through. That is why the local dev proxy rewrites those " +
  "advertised URLs to itself. " +
  "In production the discovery document must be public at the host root via an edge rewrite (design rule R6). " +
  "Note the two UCP transports are 13 identical tools/routes over one service layer."
);

pres.writeFile({ fileName: "mcp-ucp-layers.pptx" }).then((f) => console.log("wrote " + f));
