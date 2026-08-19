#!/usr/bin/env bash
# Sync the canonical stage commands into .claude/commands/cq/ so Claude Code's /cq:*
# picks them up. The skill copy (commands/*.md here) is the source of truth.
set -euo pipefail
skill_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$skill_dir/../../.." && pwd)"   # up from .claude/skills/<skill>/
dest="$repo_root/.claude/commands/cq"
mkdir -p "$dest"
cp "$skill_dir"/commands/*.md "$dest"/
echo "Synced $(ls -1 "$skill_dir"/commands/*.md | wc -l | tr -d ' ') stage commands → $dest"
