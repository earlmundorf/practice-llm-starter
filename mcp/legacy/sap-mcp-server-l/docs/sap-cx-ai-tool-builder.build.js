const pptxgen = require("pptxgenjs");

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";

const BG      = "0F1729";
const CARD_TK = "23204A";
const CARD_N  = "1A2138";
const TK       = "9B8CFA";
const MONO_FG  = "CFD8EA";
const CORE     = "93A7C6";
const WHITE    = "FFFFFF";
const MUTED    = "97A8C2";

const SERIF = "Cambria";
const SANS  = "Calibri";
const MONO  = "Courier New";

const LX = 0.5,  LW = 6.9;
const RX = 7.65, RW = 5.15;

const slide = pres.addSlide();
slide.background = { color: BG };

slide.addText("AI Tool Builder", {
  x: 0.5, y: 0.28, w: 5.6, h: 0.52, margin: 0,
  fontFace: SERIF, fontSize: 33, bold: true, color: WHITE,
});
slide.addText(
  "The toolkit is extensible — and the merge fields are the seam where a custom tool reaches into the CCv2 catalog.",
  { x: 0.5, y: 0.84, w: 11.6, h: 0.28, margin: 0, fontFace: SANS, fontSize: 12.5, color: MUTED }
);
slide.addText("Source: SAP Learning", {
  x: 9.4, y: 0.36, w: 3.4, h: 0.34, margin: 0,
  fontFace: SANS, fontSize: 9.5, color: MUTED, align: "right", italic: true,
});

function card(x, y, w, h, fill, line) {
  slide.addShape(pres.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.06,
    fill: { color: fill }, line: { color: line, width: 1 },
  });
}
function colHead(x, w, y, text) {
  slide.addText(text, {
    x, y, w, h: 0.26, margin: 0,
    fontFace: SANS, fontSize: 9, bold: true, color: TK, charSpacing: 1,
  });
}

// ---- left: the wizard ----------------------------------------------------
colHead(LX, LW, 1.30, "CREATE AI TOOL  —  WHAT THE WIZARD ASKS");

const STEPS = [
  ["Identity", "Name, short description for the tool card, long description used as the user instructions."],
  ["Licenses", "Which licenses may use the tool once it is activated."],
  ["Source Application", "Select SAP Commerce Cloud — this is what grants the tool access to product data."],
  ["Prompt", "The guiding instruction, generated against the grounded data you reference."],
  ["Default Tone and Length", "Voice (professional, enthusiastic, …) plus output size."],
  ["Special Instructions", "Optional toggle letting end users personalize at runtime — “make it sound like a summer sale.”"],
];

let y = 1.64;
STEPS.forEach(([name, desc], i) => {
  const h = 0.71;
  slide.addShape(pres.ShapeType.ellipse, {
    x: LX, y: y + 0.10, w: 0.30, h: 0.30,
    fill: { color: BG }, line: { color: TK, width: 1.25 },
  });
  slide.addText(String(i + 1), {
    x: LX, y: y + 0.10, w: 0.30, h: 0.30, margin: 0,
    fontFace: SANS, fontSize: 10.5, bold: true, color: TK,
    align: "center", valign: "middle",
  });
  slide.addText(
    [
      { text: name, options: { fontSize: 11, bold: true, color: WHITE, breakLine: true } },
      { text: desc, options: { fontSize: 9.5, color: MUTED } },
    ],
    { x: LX + 0.44, y: y + 0.04, w: LW - 0.44, h: h - 0.08,
      margin: 0, fontFace: SANS, valign: "top", paraSpaceAfter: 2 }
  );
  y += h;
});

// worked example
card(LX, 6.06, LW, 0.66, CARD_TK, "45407F");
slide.addText(
  [
    { text: "Worked example — Category Page Content Creator:  ", options: { bold: true, color: WHITE } },
    { text: "reads the products in a category and produces landing-page content surfacing their shared themes and features.", options: { color: MUTED } },
  ],
  { x: LX + 0.2, y: 6.06, w: LW - 0.4, h: 0.66, margin: 0,
    fontFace: SANS, fontSize: 9.5, valign: "middle" }
);

// ---- right: grounding ---------------------------------------------------
colHead(RX, RW, 1.30, "GROUNDING  —  THE CCv2 SEAM");
card(RX, 1.64, RW, 2.26, CARD_TK, "45407F");

const FIELDS = [
  ["{{Product.name}}", "the product name"],
  ["{{Product.*}}", "all available attributes for one product"],
  ["{{Category.name}}", "the category name"],
  ["{{Category.List.Product}}", "data from several related products in the category"],
];
const rt = [];
FIELDS.forEach(([f, d], i) => {
  rt.push({ text: f, options: { fontFace: MONO, fontSize: 9.5, bold: true, color: TK, breakLine: true } });
  rt.push({ text: d, options: { fontFace: SANS, fontSize: 9, color: MONO_FG, breakLine: i !== FIELDS.length - 1 } });
});
slide.addText(rt, {
  x: RX + 0.2, y: 1.76, w: RW - 0.4, h: 1.72,
  margin: 0, valign: "top", paraSpaceAfter: 5,
});
slide.addText("Category.List.Product is the collection-level reference — what makes cross-product copy possible instead of per-product copy.", {
  x: RX + 0.2, y: 3.50, w: RW - 0.4, h: 0.34, margin: 0,
  fontFace: SANS, fontSize: 8.5, color: TK, italic: true, valign: "middle",
});

colHead(RX, RW, 4.02, "PROMPT CRAFT");
card(RX, 4.36, RW, 1.28, CARD_N, "313A5C");
const PC = [
  "State the role — “you are an expert e-commerce copywriter…”",
  "State the outcome precisely — “a 3-paragraph summary”",
  "Zero-shot (no samples) vs few-shot (1–2 to steer style)",
  "Keep PII out of the prompt field entirely",
];
slide.addText(
  PC.map((t, i) => ({
    text: t,
    options: {
      fontFace: SANS, fontSize: 9.5, color: i === 3 ? TK : MUTED, bold: i === 3,
      bullet: { code: "25AA", color: TK, indent: 13 },
      breakLine: i !== PC.length - 1,
    },
  })),
  { x: RX + 0.2, y: 4.46, w: RW - 0.4, h: 1.10, margin: 0, valign: "top", paraSpaceAfter: 4 }
);

// caveat
card(RX, 5.78, RW, 0.66, CARD_N, "313A5C");
slide.addText(
  [
    { text: "Not in the source lesson: ", options: { bold: true, color: CORE } },
    { text: "model selection, API exposure, agent integration, named roles, or quantitative limits.", options: { color: MUTED } },
  ],
  { x: RX + 0.2, y: 5.78, w: RW - 0.4, h: 0.66, margin: 0,
    fontFace: SANS, fontSize: 9, valign: "middle" }
);

// ---- footer -------------------------------------------------------------
slide.addText(
  [
    { text: "Why it matters: ", options: { bold: true, color: WHITE } },
    { text: "the shipped tools (social posts, blog posts, banner text) cover common cases. AI Tool Builder is how a bespoke prompt gets grounded in your own catalog.", options: { color: MUTED } },
  ],
  { x: 0.5, y: 6.86, w: 12.3, h: 0.32, margin: 0, fontFace: SANS, fontSize: 9.5, valign: "middle" }
);

slide.addNotes(
  "This slide corrects a natural assumption about the toolkit: that you are limited to what SAP ships. " +
  "For AI TOOLS you are not -- AI Tool Builder lets an admin author a custom tool with its own prompt, its own tone and length " +
  "defaults, and its own grounding against catalog data, then activate it for chosen licenses. " +
  "Be precise about the boundary, though: the lesson covers authoring custom content-generation TOOLS. It does not describe " +
  "authoring custom SUBAGENTS for the Shopping Agent -- those five built-ins are still toggled, not written. " +
  "The merge fields are the part worth dwelling on, because they are the concrete form of 'it sits on top of CCv2'. " +
  "Category.List.Product is the interesting one: it pulls several related products from a category in one reference, which is " +
  "what makes collection-level copy possible rather than per-product copy. " +
  "Selecting SAP Commerce Cloud as the Source Application is literally the step that grants catalog access. " +
  "Two honest gaps to flag if asked: the lesson names no model selection and no API surface for built tools, and SAP Help Portal " +
  "renders via JavaScript so those pages could not be read here. Do not assert BTP, AI Core, or generative AI hub as the substrate -- " +
  "the lessons say only 'cloud-native'."
);

pres.writeFile({ fileName: "sap-cx-ai-tool-builder.pptx" }).then((f) => console.log("wrote " + f));
