#!/usr/bin/env python3
"""Build the VS Code chat + Claude gateway setup guide as a PDF.

    /tmp/pdfvenv/bin/python docs/build-vscode-gateway-pdf.py

Regenerate after editing the CONTENT section below. No API keys belong in here —
the key is entered once into VS Code's secret storage, never into a file.
"""

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    KeepTogether,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

OUT = "docs/vscode-chat-claude-gateway-setup.pdf"
TITLE = "VS Code Chat — Claude Gateway Setup"
SUBTITLE = "Connecting the built-in VS Code chat to the Capgemini Claude gateway"

INK = colors.HexColor("#1a1a1a")
MUTED = colors.HexColor("#5c5c5c")
RULE = colors.HexColor("#d8d8d8")
ACCENT = colors.HexColor("#0a5c8a")
CODE_BG = colors.HexColor("#f4f6f8")
CODE_RULE = colors.HexColor("#dfe4e8")
WARN_BG = colors.HexColor("#fdf6e3")
WARN_RULE = colors.HexColor("#e8d9a8")

MARGIN = 0.85 * inch
PAGE_W, PAGE_H = LETTER
BODY_W = PAGE_W - 2 * MARGIN

_ss = getSampleStyleSheet()

S = {
    "title": ParagraphStyle(
        "title", parent=_ss["Title"], fontName="Helvetica-Bold",
        fontSize=21, leading=25, textColor=INK, alignment=TA_LEFT, spaceAfter=3,
    ),
    "subtitle": ParagraphStyle(
        "subtitle", parent=_ss["Normal"], fontName="Helvetica",
        fontSize=10.5, leading=14, textColor=MUTED, spaceAfter=14,
    ),
    "h2": ParagraphStyle(
        "h2", parent=_ss["Heading2"], fontName="Helvetica-Bold",
        fontSize=13, leading=16, textColor=ACCENT, spaceBefore=16, spaceAfter=6,
    ),
    "h3": ParagraphStyle(
        "h3", parent=_ss["Heading3"], fontName="Helvetica-Bold",
        fontSize=10.5, leading=13, textColor=INK, spaceBefore=10, spaceAfter=4,
    ),
    "body": ParagraphStyle(
        "body", parent=_ss["BodyText"], fontName="Helvetica",
        fontSize=9.8, leading=14, textColor=INK, spaceAfter=7,
    ),
    "step": ParagraphStyle(
        "step", parent=_ss["BodyText"], fontName="Helvetica",
        fontSize=9.8, leading=14, textColor=INK,
        leftIndent=16, firstLineIndent=-16, spaceAfter=5,
    ),
    "bullet": ParagraphStyle(
        "bullet", parent=_ss["BodyText"], fontName="Helvetica",
        fontSize=9.8, leading=14, textColor=INK,
        leftIndent=14, firstLineIndent=-9, spaceAfter=5,
    ),
    "code": ParagraphStyle(
        "code", parent=_ss["Code"], fontName="Courier",
        fontSize=8.3, leading=11.4, textColor=INK,
        leftIndent=0, rightIndent=0, firstLineIndent=0, spaceBefore=0, spaceAfter=0,
    ),
    "cell": ParagraphStyle(
        "cell", parent=_ss["BodyText"], fontName="Helvetica",
        fontSize=8.8, leading=12, textColor=INK, spaceAfter=0,
    ),
    "cellhead": ParagraphStyle(
        "cellhead", parent=_ss["BodyText"], fontName="Helvetica-Bold",
        fontSize=8.8, leading=12, textColor=colors.white, spaceAfter=0,
    ),
    "cellmono": ParagraphStyle(
        "cellmono", parent=_ss["BodyText"], fontName="Courier",
        fontSize=8.5, leading=12, textColor=INK, spaceAfter=0,
    ),
    "footer": ParagraphStyle(
        "footer", parent=_ss["Normal"], fontName="Helvetica",
        fontSize=7.8, leading=10, textColor=MUTED,
    ),
}


CODE_COLS = 92  # widest line that fits BODY_W at Courier 8.3pt inside 9pt padding


def code(text, bg=CODE_BG, rule=CODE_RULE):
    """A code block rendered one line per table row, so it can break across pages
    instead of leaving a large gap when it does not fit the remaining space."""
    lines = text.strip("\n").split("\n")
    over = [(i + 1, len(l)) for i, l in enumerate(lines) if len(l) > CODE_COLS]
    if over:
        raise SystemExit(
            f"code block line(s) exceed {CODE_COLS} cols and would clip: {over}")
    rows = [[Preformatted(l if l.strip() else " ", S["code"])] for l in lines]
    t = Table(rows, colWidths=[BODY_W])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("BOX", (0, 0), (-1, -1), 0.6, rule),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (-1, -1), 0),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (0, 0), 8),
        ("BOTTOMPADDING", (0, -1), (-1, -1), 8),
    ]))
    return t


def callout(label, text):
    body = Paragraph(f"<b>{label}</b>&nbsp; {text}", S["cell"])
    t = Table([[body]], colWidths=[BODY_W])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), WARN_BG),
        ("BOX", (0, 0), (-1, -1), 0.6, WARN_RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    return t


def table(headers, rows, widths, mono_cols=()):
    data = [[Paragraph(h, S["cellhead"]) for h in headers]]
    for r in rows:
        data.append([
            Paragraph(c, S["cellmono"] if i in mono_cols else S["cell"])
            for i, c in enumerate(r)
        ])
    t = Table(data, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), ACCENT),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, RULE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#fafbfc")]),
    ]))
    return t


GATEWAY = "https://anthropic.generative.engine.capgemini.com"
ENDPOINT = f"{GATEWAY}/v1/messages"

MODELS_JSON = """
"apiType": "messages",
"models": [
  {
    "id": "claude-opus-5",
    "name": "Claude Opus 5",
    "url": "%s",
    "toolCalling": true,
    "vision": true,
    "maxInputTokens": 128000,
    "maxOutputTokens": 16000
  },
  {
    "id": "claude-sonnet-5",
    "name": "Claude Sonnet 5",
    "url": "%s",
    "toolCalling": true,
    "vision": true,
    "maxInputTokens": 128000,
    "maxOutputTokens": 16000
  }
]
""" % (ENDPOINT, ENDPOINT)

SONNET_ONLY = """
{
  "id": "claude-sonnet-5",
  "name": "Claude Sonnet 5",
  "url": "%s",
  "toolCalling": true,
  "vision": true,
  "maxInputTokens": 128000,
  "maxOutputTokens": 16000
}
""" % ENDPOINT


def build_story():
    s = []
    s.append(Paragraph(TITLE, S["title"]))
    s.append(Paragraph(SUBTITLE, S["subtitle"]))

    s.append(Paragraph(
        "This connects the <b>built-in VS Code chat</b> (Copilot Chat) to the Capgemini Claude "
        "gateway using your existing API key and base URL, via VS Code's <b>Custom Endpoint</b> "
        "bring-your-own-key provider. No GitHub Copilot subscription is required for chat with "
        "your own models.", S["body"]))

    s.append(Paragraph("Before you start", S["h2"]))
    s.append(Paragraph(
        "&bull;&nbsp; VS Code with the Chat view available.", S["bullet"]))
    s.append(Paragraph(
        f"&bull;&nbsp; Your gateway base URL: <font face='Courier'>{GATEWAY}</font>", S["bullet"]))
    s.append(Paragraph(
        "&bull;&nbsp; Your gateway API key (starts with <font face='Courier'>sk-</font>). "
        "You will paste it into VS Code once; it is stored in VS Code secret storage, "
        "not in any file.", S["bullet"]))

    s.append(Paragraph("Step by step", S["h2"]))
    steps = [
        "Open the Command Palette with <b>Cmd+Shift+P</b> (macOS) or <b>Ctrl+Shift+P</b> "
        "(Windows / Linux).",
        "Run <b>Chat: Manage Language Models</b>.",
        "Choose <b>Add Models</b>, then <b>Custom Endpoint</b>.",
        "Enter a group name: <font face='Courier'>capgemini</font>",
        "Enter a display name: <font face='Courier'>Capgemini Gateway</font>",
        "Paste your API key when prompted.",
        "For the API type, choose <b>Anthropic Messages</b>.",
        "VS Code opens <font face='Courier'>chatLanguageModels.json</font>. Fill in the model "
        "details using the block below. On macOS the file lives at "
        "<font face='Courier'>~/Library/Application Support/Code/User/"
        "chatLanguageModels.json</font>, so you can reopen it later without going through "
        "this menu again.",
        "Save the file, then run <b>Developer: Reload Window</b> from the Command Palette.",
        "Open the chat model picker. <b>Claude Opus 5</b> and <b>Claude Sonnet 5</b> now appear.",
    ]
    for i, txt in enumerate(steps, 1):
        s.append(Paragraph(f"<b>{i}.</b>&nbsp; {txt}", S["step"]))

    s.append(Paragraph("Configuration to paste", S["h2"]))
    s.append(Paragraph(
        "The file is a JSON <b>array of provider objects</b>. Keep the object VS Code generated — "
        "it already carries the correct <font face='Courier'>vendor</font> and your stored key "
        "reference — and replace only its <font face='Courier'>apiType</font> and "
        "<font face='Courier'>models</font> entries with the following. "
        "<font face='Courier'>apiType</font> sits at the provider level, so it covers every model "
        "in the array.", S["body"]))
    s.append(code(MODELS_JSON))

    s.append(Paragraph("Adding Sonnet to an existing setup", S["h2"]))
    s.append(Paragraph(
        "If Opus 5 already works and you only need to add Sonnet, add a comma after the Opus "
        "entry's closing brace and paste this as the second array element:", S["body"]))
    s.append(code(SONNET_ONLY))

    s.append(Paragraph("Three things that will break it", S["h2"]))
    s.append(KeepTogether([
        Paragraph(
            "&bull;&nbsp; <b>The URL must include the full <font face='Courier'>/v1/messages</font> "
            "path.</b> The bare origin works for the Claude CLI because it appends the path itself. "
            "VS Code needs it spelled out, or it infers one.", S["bullet"]),
        Paragraph(
            "&bull;&nbsp; <b><font face='Courier'>toolCalling: true</font> is mandatory.</b> "
            "Without it the model is silently absent from the picker — the most common "
            "“I configured it and nothing showed up” cause.", S["bullet"]),
        Paragraph(
            "&bull;&nbsp; <b><font face='Courier'>maxInputTokens</font> + "
            "<font face='Courier'>maxOutputTokens</font> must not exceed the context window.</b> "
            "128000 + 16000 sits well inside the 200K window and is the pairing verified here.", S["bullet"]),
    ]))
    s.append(Spacer(1, 4))
    s.append(callout(
        "No auth header override needed.",
        f"The <font face='Courier'>messages</font> API type defaults to "
        f"<font face='Courier'>x-api-key</font>, which is what this gateway expects. "
        f"Verified: <font face='Courier'>POST {ENDPOINT}</font> returns HTTP 200."))

    s.append(Paragraph("A note on skills", S["h2"]))
    s.append(Paragraph(
        "VS Code chat reads Agent Skills from <font face='Courier'>.claude/skills/</font>, "
        "<font face='Courier'>.github/skills/</font> and "
        "<font face='Courier'>.agents/skills/</font> in the workspace, and from "
        "<font face='Courier'>~/.claude/skills/</font>, "
        "<font face='Courier'>~/.copilot/skills/</font> and "
        "<font face='Courier'>~/.agents/skills/</font> for your user profile. Skills installed by "
        "the QRSPI kit are therefore picked up with no extra configuration, and work with either "
        "the gateway models above or Copilot's own. In a monorepo, opening the repository root "
        "will not find skills nested inside subprojects — point VS Code at them with the "
        "<font face='Courier'>chat.agentSkillsLocations</font> setting, or set "
        "<font face='Courier'>chat.useCustomizationsInParentRepositories</font> when working from "
        "a subfolder.", S["body"]))

    trouble_table = table(
        ["Symptom", "Cause and fix"],
        [
            ["Model missing from the picker",
             "<font face='Courier'>toolCalling</font> not set to "
             "<font face='Courier'>true</font>, or the window was not reloaded. "
             "Run <b>Developer: Reload Window</b>."],
            ["HTTP 400 from the gateway",
             "Unrecognised <font face='Courier'>id</font>. Use "
             "<font face='Courier'>claude-opus-5</font> or "
             "<font face='Courier'>claude-sonnet-5</font> exactly as shown above."],
            ["HTTP 401 or 403",
             "Key not stored or expired. Re-run <b>Chat: Manage Language Models</b> and "
             "re-enter it via the gear icon beside the provider."],
            ["Picker shows only “Auto”",
             "The workspace is in Restricted Mode. Trust the workspace."],
            ["Works in chat, not in an Agent session",
             "Agent Host sessions need the experimental setting "
             "<font face='Courier'>chat.agentHost.byokModels.enabled</font>."],
        ],
        widths=[BODY_W * 0.31, BODY_W * 0.69],
    )
    s.append(KeepTogether([Paragraph("Troubleshooting", S["h2"]), trouble_table]))

    return s


def on_page(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.5)
    y = MARGIN - 0.22 * inch
    canvas.line(MARGIN, y, PAGE_W - MARGIN, y)
    canvas.setFont("Helvetica", 7.8)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN, y - 11, "VS Code Chat — Claude Gateway Setup")
    canvas.drawRightString(PAGE_W - MARGIN, y - 11, f"Page {doc.page}")
    canvas.restoreState()


def main():
    doc = BaseDocTemplate(
        OUT, pagesize=LETTER,
        leftMargin=MARGIN, rightMargin=MARGIN,
        topMargin=MARGIN, bottomMargin=MARGIN,
        title=TITLE, author="Capgemini SAP CX", subject=SUBTITLE,
        creator="Capgemini SAP CX",
    )
    frame = Frame(MARGIN, MARGIN, BODY_W, PAGE_H - 2 * MARGIN, id="body",
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    doc.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=on_page)])
    doc.build(build_story())
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
