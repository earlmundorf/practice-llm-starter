# Restoring the QRSPI demo (clean checkout → running)

Everything is committed and tagged locally. The demo branch is **intentionally local** —
it is never pushed to remote. To get the whole environment back, check out the tag and
bring up the services below.

## Git anchors

| Ref | SHA | What it is |
|---|---|---|
| `veritiv-ai-approach` | branch tip `8f90462` | working branch; tip = full demo state |
| `qrspi-demo-v2` (tag) | `23f23eb` | **durable restore point** — canonical skills + `qrspi-kit/` + THINK-142 backend + demo console/tickets. Checkout to recover everything. |
| `qrspi-demo-baseline` (tag) | `5126f41` | the pre-tooling "before" state `reset.sh` **full** resets to. NOT an ancestor of the branch (built via the worktree-fold technique). |
| `backup/pre-143-cleanup` | `9513f01` | pre-rebase safety ref |

**Restore in one line:** `git checkout qrspi-demo-v2`  (or `git checkout veritiv-ai-approach`).

## Prerequisites (as configured on this machine — verify before relying on them)

- **Java 17** — `/Users/emundorf/.sdkman/candidates/java/17.0.19-sapmchn` (legacy backend build; the shell defaults to Java 21, so set `JAVA_HOME` explicitly)
- **Node** via nvm — `export PATH="/Users/emundorf/.nvm/versions/node/v24.14.0/bin:$PATH"`
- **Docker** via colima — add `/opt/homebrew/Cellar/docker/<ver>/bin` to PATH, `colima start`, then start the MySQL container
- **ttyd** — `/opt/homebrew/bin/ttyd`
- **Pre-flight:** stop any other Commerce servers first (ports 9001/9002/8983) — `startServer` succeeds silently if the HTTP ports are taken.

## Bring-up from a clean checkout

```bash
git checkout qrspi-demo-v2

# 1) MySQL (one container; one DB per project dir)
colima start && docker start hybris-mysql

# 2) Backend — legacy, Java 17
cd mcp/legacy/sap-mcp-server-l/core-customize
export JAVA_HOME=/Users/emundorf/.sdkman/candidates/java/17.0.19-sapmchn
./gradlew yclean yall            # add yinitialize only if the DB is empty
./gradlew startServer
./scripts/index-solr.sh && ./scripts/setup-promotions.sh
./gradlew groovy -Pfile=scripts/publish-promotions.groovy -Pcommit=true

# 3) Storefront (:5173)
cd ../../sap-mcp-ui-l
export PATH="/Users/emundorf/.nvm/versions/node/v24.14.0/bin:$PATH"
npm install && npm run dev

# 4) Console (:8090) + terminal (:7681)
node demo/qrspi-demo/serve.mjs &
/opt/homebrew/bin/ttyd -W -t fontSize=15 -t 'theme={"background":"#0b0b0f"}' zsh -l &
open http://localhost:8090
```

## Re-running the demo

- **Safe UI re-run:** `bash demo/qrspi-demo/reset.sh ui` — reverts the storefront to baseline,
  leaves the 142 backend running, clears `working-docs/THINK-143`. Then drive
  `/cq:0_go THINK-143 simple` in the terminal.
- **Full rebuild:** `bash demo/qrspi-demo/reset.sh` — ⚠️ this does `git reset --hard qrspi-demo-baseline`
  (`5126f41`), which does **not** contain the canonical skills, `qrspi-kit/`, or the latest console,
  and drops the branch's commits. Use it only for a from-scratch 142+143 rebuild, and afterward
  recover the full state with `git checkout qrspi-demo-v2`.
- If you resume demoing regularly and want the reset loop to reflect the current console/skills,
  re-fold the baseline tag (worktree off `qrspi-demo-baseline`, copy in the latest
  `demo/qrspi-demo/*`, commit, `git tag -f qrspi-demo-baseline <sha>`) — the durable state stays
  safe on `qrspi-demo-v2` regardless.

## Merging the durable work to main (demo stays local)

`origin` has no `veritiv-ai-approach` and it must stay that way. Only the two **durable** commits
belong on `main` — the skills and the handoff kit — never the demo loop.

> Push is currently **blocked**: `git fetch`/push fail auth on the `github-individual` SSH host.
> Fix SSH access first, then:

```bash
git fetch origin
git switch -c qrspi-durable origin/main
git cherry-pick 829b980   # A: qrspi skills (grounding, retire RPI, reconcile refs)
git cherry-pick e61ba03   # B: qrspi-kit canonical handoff kit
git push -u origin qrspi-durable      # open a PR → main
```

Do **not** cherry-pick `8f90462` (commit C, the demo loop) or push `veritiv-ai-approach`.
