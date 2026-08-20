#!/usr/bin/env node
// Demo server for the commerce-qrspi / storefront-qrspi briefing page.
//   node serve.mjs
// Serves ticket.html on :PORT and exposes, per ticket:
//   GET /tree?ticket=THINK-142            live recursive listing of that ticket's working-docs run dir
//   GET /file?ticket=THINK-142&path=x.md  raw contents of one file under that run dir (path-safe)
// so the page shows artifacts appear in real time and renders them.
//
// Env:  PORT (default 8090)

import { createServer } from "node:http";
import { readFile, readdir, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve, sep } from "node:path";

const PORT = Number(process.env.PORT || 8090);
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = "/Users/emundorf/development/mundo-dev/projects/practice-llm-starter";

// ticket → its QRSPI working-docs run dir (142 = legacy backend, 143 = sibling UI repo)
const RUNDIRS = {
  "THINK-142": `${ROOT}/mcp/legacy/sap-mcp-server-l/working-docs/THINK-142`,
  "THINK-143": `${ROOT}/mcp/legacy/sap-mcp-ui-l/working-docs/THINK-143`,
};
const dirFor = (t) => RUNDIRS[t] || RUNDIRS["THINK-142"];

async function walk(dir, base, out) {
  let entries;
  try { entries = await readdir(dir, { withFileTypes: true }); } catch { return out; }
  for (const e of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    if (e.name.startsWith(".")) continue;
    const full = join(dir, e.name);
    let s; try { s = await stat(full); } catch { continue; }
    out.push({ path: relative(base, full), dir: e.isDirectory(), size: s.size, mtime: s.mtimeMs });
    if (e.isDirectory()) await walk(full, base, out);
  }
  return out;
}

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const ticket = url.searchParams.get("ticket") || "THINK-142";
  const WORKDIR = dirFor(ticket);

  if (url.pathname === "/tree") {
    const exists = await stat(WORKDIR).then(() => true).catch(() => false);
    const files = exists ? await walk(WORKDIR, WORKDIR, []) : [];
    let pr = { url: null, bodyFile: null };
    if (exists) {
      const mds = files.filter(f => !f.dir && f.path.endsWith(".md")).map(f => f.path);
      pr.bodyFile = ["pr.md", "pull-request.md", "validation.md"].find(p => mds.includes(p)) || null;
      for (const p of mds) {
        try {
          const m = (await readFile(join(WORKDIR, p), "utf8")).match(/https:\/\/github\.com\/[^\s)>\]"']+\/pull\/\d+/);
          if (m) { pr.url = m[0]; break; }
        } catch { /* ignore */ }
      }
    }
    res.writeHead(200, { "Content-Type": "application/json", "Cache-Control": "no-store" });
    return res.end(JSON.stringify({ ticket, root: `working-docs/${ticket}`, exists, files, pr }));
  }

  if (url.pathname === "/file") {
    const rel = url.searchParams.get("path") || "";
    const target = resolve(join(WORKDIR, rel));
    const base = resolve(WORKDIR);
    if (target !== base && !target.startsWith(base + sep)) { res.writeHead(403); return res.end("forbidden"); }
    try {
      const txt = await readFile(target, "utf8");
      res.writeHead(200, { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" });
      return res.end(txt);
    } catch { res.writeHead(404); return res.end("not found"); }
  }

  if (["/", "/index.html", "/ticket.html"].includes(url.pathname)) {
    try {
      const html = await readFile(join(HERE, "ticket.html"));
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
      return res.end(html);
    } catch { res.writeHead(500); return res.end("ticket.html not found"); }
  }
  res.writeHead(404); res.end("not found");
}).listen(PORT, () => {
  console.log(`qrspi demo → http://localhost:${PORT}`);
  for (const [t, d] of Object.entries(RUNDIRS)) console.log(`  ${t} → ${d}`);
});
