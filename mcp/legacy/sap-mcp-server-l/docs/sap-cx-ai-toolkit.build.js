const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.3 x 7.5

// ---- palette (continuous with the MCP/UCP slide) -------------------------
const BG        = "0F1729";
const CARD_TK   = "23204A"; // toolkit violet tint
const CARD_CORE = "1C2740"; // CCv2 slate — same as the prior slide's platform tier
const TK        = "9B8CFA";
const CORE      = "93A7C6";
const WHITE     = "FFFFFF";
const MUTED     = "97A8C2";

const SERIF = "Cambria";
const SANS  = "Calibri";

// ---- geometry ------------------------------------------------------------
const GUT_X = 0.5, GUT_W = 1.02;
const X = 1.66, W = 11.12;

const ROW = {
  d: { y: 1.42, h: 0.62 }, // surfaces        (tier 4)
  c: { y: 2.26, h: 1.00 }, // agents & tools  (tier 3)
  b: { y: 3.48, h: 1.52 }, // subagents       (tier 2)
  a: { y: 5.22, h: 0.88 }, // CCv2            (tier 1)
};

const slide = pres.addSlide();
slide.background = { color: BG };

// ---- title ---------------------------------------------------------------
slide.addText("SAP CX AI Toolkit on Commerce Cloud", {
  x: 0.5, y: 0.28, w: 8.5, h: 0.52, margin: 0,
  fontFace: SERIF, fontSize: 33, bold: true, color: WHITE,
});
slide.addText(
  "It sits on top of CCv2 — every subagent rides a Commerce Cloud capability that already works. No platform extension is deployed.",
  { x: 0.5, y: 0.84, w: 11.6, h: 0.28, margin: 0, fontFace: SANS, fontSize: 12.5, color: MUTED }
);
slide.addText("Source: SAP Learning", {
  x: 9.4, y: 0.36, w: 3.4, h: 0.34, margin: 0,
  fontFace: SANS, fontSize: 9.5, color: MUTED, align: "right", italic: true,
});

// ---- helpers -------------------------------------------------------------
function card(x, y, w, h, fill, line) {
  slide.addShape(pres.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.06,
    fill: { color: fill }, line: { color: line, width: 1 },
  });
}

function tier(n, label, y, h) {
  slide.addShape(pres.ShapeType.ellipse, {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31,
    fill: { color: BG }, line: { color: CORE, width: 1.25 },
  });
  slide.addText(String(n), {
    x: GUT_X, y: y + h / 2 - 0.155, w: 0.31, h: 0.31, margin: 0,
    fontFace: SANS, fontSize: 11, bold: true, color: CORE,
    align: "center", valign: "middle",
  });
  slide.addText(label, {
    x: GUT_X, y: y + h / 2 + 0.16, w: GUT_W, h: 0.34, margin: 0,
    fontFace: SANS, fontSize: 8.5, bold: true, color: MUTED, charSpacing: 0.8,
  });
}

function downArrow(cx, y, color) {
  slide.addShape(pres.ShapeType.downArrow, {
    x: cx - 0.09, y, w: 0.18, h: 0.17,
    fill: { color }, line: { color, width: 0 },
  });
}

// ---- tier 4 — surfaces ---------------------------------------------------
tier(4, "SURFACES", ROW.d.y, ROW.d.h);
card(X, ROW.d.y, W, ROW.d.h, CARD_TK, "45407F");
slide.addText(
  [
    { text: "Storefront widget", options: { bold: true, color: WHITE } },
    { text: "   ·   SAP Commerce Cloud UIs   ·   Joule AI Action Bar   ·   web + mobile   ·   replies in the shopper's language", options: { color: MUTED } },
  ],
  { x: X + 0.22, y: ROW.d.y, w: W - 0.44, h: ROW.d.h, margin: 0,
    fontFace: SANS, fontSize: 11, valign: "middle" }
);
downArrow(X + W / 2, ROW.d.y + ROW.d.h + 0.03, TK);

// ---- tier 3 — agents & AI tools -----------------------------------------
tier(3, "AGENTS", ROW.c.y, ROW.c.h);
card(X, ROW.c.y, W, ROW.c.h, CARD_TK, "45407F");
slide.addText(
  [
    { text: "AGENTS      ", options: { bold: true, fontSize: 9, color: TK, charSpacing: 1 } },
    { text: "Shopping Agent   ·   Digital Service Agent   ·   Catalog Optimization Agent", options: { fontSize: 11.5, color: WHITE, bold: true, breakLine: true } },
    { text: "AI TOOLS    ", options: { bold: true, fontSize: 9, color: TK, charSpacing: 1 } },
    { text: "Product Attributes  ·  Product Descriptions  ·  Product Images  ·  Visual Search  ·  AI Tool Builder", options: { fontSize: 10.5, color: MUTED } },
  ],
  { x: X + 0.22, y: ROW.c.y + 0.16, w: W - 0.44, h: ROW.c.h - 0.32,
    margin: 0, valign: "top", paraSpaceAfter: 7 }
);
slide.addText("Tone: Neutral / Casual / Formal      Detail: Short / Medium / Long", {
  x: X + 0.22, y: ROW.c.y + ROW.c.h - 0.30, w: W - 0.44, h: 0.24, margin: 0,
  fontFace: SANS, fontSize: 9, color: TK, italic: true, valign: "middle",
});
downArrow(X + W / 2, ROW.c.y + ROW.c.h + 0.03, TK);

// ---- tier 2 — the five built-in subagents, and what each rides on -------
tier(2, "SUBAGENTS", ROW.b.y, ROW.b.h);
const SUB = [
  { n: "Add to Cart",          d: "puts requested products in the cart",           cc: "cart" },
  { n: "Order Status",         d: "real-time tracking + shipping updates",         cc: "order history" },
  { n: "Price Recommendation", d: "compares cost, surfaces savings",               cc: "live pricing + promotions" },
  { n: "Product Availability", d: "quantities and current stock levels",           cc: "inventory" },
  { n: "Digital Service Agent", d: "answers from uploaded PDFs and documentation", cc: "toolkit-side RAG — not CCv2", ext: true },
];
const CW = (W - 4 * 0.16) / 5;
SUB.forEach((s, i) => {
  const cx = X + i * (CW + 0.16);
  const accent = s.ext ? CORE : TK;
  card(cx, ROW.b.y, CW, ROW.b.h, s.ext ? CARD_CORE : CARD_TK, s.ext ? "35486B" : "45407F");
  slide.addText(s.n, {
    x: cx + 0.14, y: ROW.b.y + 0.14, w: CW - 0.28, h: 0.46, margin: 0,
    fontFace: SANS, fontSize: 11, bold: true, color: WHITE, valign: "top",
  });
  slide.addText(s.d, {
    x: cx + 0.14, y: ROW.b.y + 0.62, w: CW - 0.28, h: 0.46, margin: 0,
    fontFace: SANS, fontSize: 9, color: MUTED, valign: "top",
  });
  slide.addText(
    [
      { text: s.ext ? "source  " : "rides on  ", options: { fontSize: 7.5, color: accent, charSpacing: 0.6 } },
      { text: s.cc, options: { fontSize: 8.5, color: accent, bold: true } },
    ],
    { x: cx + 0.14, y: ROW.b.y + ROW.b.h - 0.36, w: CW - 0.28, h: 0.28,
      margin: 0, fontFace: SANS, valign: "middle" }
  );
  if (!s.ext) downArrow(cx + CW / 2, ROW.b.y + ROW.b.h + 0.03, TK);
});

// ---- tier 1 — SAP Commerce Cloud ---------------------------------------
tier(1, "CCv2", ROW.a.y, ROW.a.h);
card(X, ROW.a.y, W, ROW.a.h, CARD_CORE, "35486B");
slide.addText("SAP Commerce Cloud (CCv2) — standard capabilities, unchanged", {
  x: X + 0.22, y: ROW.a.y + 0.10, w: W - 0.44, h: 0.26, margin: 0,
  fontFace: SANS, fontSize: 11.5, bold: true, color: WHITE,
});
slide.addText(
  "Product catalog (synced to the online version)  ·  price rows + promotions  ·  stock levels  ·  cart  ·  orders  ·  customer",
  { x: X + 0.22, y: ROW.a.y + 0.40, w: W - 0.44, h: 0.40, margin: 0,
    fontFace: SANS, fontSize: 10, color: CORE, valign: "top" }
);

// ---- trusted AI layer (cross-cutting) ----------------------------------
card(X, 6.26, W, 0.44, "1A2138", "313A5C");
slide.addText(
  [
    { text: "Trusted AI layer   ", options: { bold: true, color: TK } },
    { text: "secure retrieval on user credentials  ·  RAG context grounding  ·  obfuscation + bias filtering  ·  zero data retention  ·  audit logs with trace IDs  ·  human in the loop",
      options: { color: MUTED } },
  ],
  { x: X + 0.22, y: 6.26, w: W - 0.44, h: 0.44, margin: 0,
    fontFace: SANS, fontSize: 9.5, valign: "middle" }
);

// ---- footer -------------------------------------------------------------
slide.addText(
  [
    { text: "Separately purchasable cloud tenant. ", options: { bold: true, color: WHITE } },
    { text: "Setup is mapping, not deployment: map the toolkit tenant to the CCv2 tenant · sync the catalog to the online version · activate the storefront and toggle subagents via API.", options: { color: MUTED } },
  ],
  { x: 0.5, y: 6.80, w: 12.3, h: 0.32, margin: 0, fontFace: SANS, fontSize: 9.5, valign: "middle" }
);

slide.addNotes(
  "The point of this slide is the direction of the arrows: everything the toolkit does resolves down into CCv2 capabilities that already work. " +
  "Add to Cart is the cart. Order Status is order history. Price Recommendation is price rows and promotions. Product Availability is stock. " +
  "Only the Digital Service Agent sources data from outside CCv2 -- uploaded PDFs and documentation, retrieved toolkit-side -- which is why that card is styled differently. " +
  "Contrast with the previous slide: coremcp and ucpcommerce are extensions compiled into the platform and served from OCC endpoints. " +
  "The CX AI Toolkit is the opposite shape -- a separately licensed, cloud-native tenant that is mapped to the CCv2 tenant and reaches in over APIs. " +
  "There is no extension to build, no yclean/ybuild cycle, and no custom endpoint. On the tradeoff, be precise: custom AI TOOLS are authorable " +
  "via AI Tool Builder, grounded in catalog merge fields. The five Shopping Agent SUBAGENTS, though, are toggled rather than written -- " +
  "so agent behaviour is bounded by what SAP ships, whereas an in-platform extension can expose anything in the service layer. " +
  "Two practical prerequisites bite in demos: the tenant mapping must exist before the agent is created, and the catalog must be synced to the ONLINE version " +
  "or the agent cannot see products. Subagent activation is API-based configuration, not a UI toggle."
);

pres.writeFile({ fileName: "sap-cx-ai-toolkit.pptx" }).then((f) => console.log("wrote " + f));
