#!/usr/bin/env bash
# Detect the current Java / Spring / Commerce state of a target project.
# Emits a structured report the skill's plan phase consumes.
#
# Usage: detect_state.sh [<project-dir>]
#        (defaults to the current working directory)
#
# CCv2 layout assumption: SAP Commerce project files (manifest.json,
# gradle.properties, build.gradle, dev-config/localextensions.xml,
# dependencies/hybris-commerce-suite-*.zip, hybris/bin/platform/setantenv.sh,
# etc.) live under PROJECT/core-customize/, not at PROJECT/. This is the
# CCv2 (Commerce Cloud v2) repository convention.

set -uo pipefail   # no -e: detect_state should never silently fail; we report
                   # what we can find and continue, rather than stopping at the
                   # first absent file.

PROJECT="${1:-$PWD}"
if [[ ! -d "$PROJECT" ]]; then
  echo "ERROR: $PROJECT is not a directory" >&2
  exit 1
fi
cd "$PROJECT"

# CCv2 root. If absent (rare — non-CCv2 layout) we fall back to PROJECT itself.
CC_DIR="$PROJECT/core-customize"
[[ -d "$CC_DIR" ]] || CC_DIR="$PROJECT"

report() { printf '%-30s %s\n' "$1:" "$2"; }
hdr()    { printf '\n== %s ==\n' "$1"; }

hdr "Project"
report "path" "$PROJECT"
report "ccv2 root" "$CC_DIR"
report "git_branch" "$(git -C "$PROJECT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo '(not a git repo)')"
report "git_head" "$(git -C "$PROJECT" rev-parse --short HEAD 2>/dev/null || echo '-')"

hdr "Java / toolchain"

# Project-root files (toolchain hints): .sdkmanrc, .tool-versions
for f in .sdkmanrc .tool-versions; do
  [[ -f "$PROJECT/$f" ]] || continue
  v=$(grep -oE 'java[[:space:]=]+[^ ]+' "$PROJECT/$f" | head -1 || true)
  [[ -n "$v" ]] && report "$f" "$v"
done

# CCv2 files: gradle.properties + manifest.json
if [[ -f "$CC_DIR/gradle.properties" ]]; then
  v=$(grep -oE 'org\.gradle\.java\.(home|toolchain)[[:space:]]*=[[:space:]]*[^#]+' "$CC_DIR/gradle.properties" | head -1 || true)
  [[ -n "$v" ]] && report "gradle.properties" "$v"
fi

if [[ -f "$CC_DIR/manifest.json" ]]; then
  cver=$(grep -oE '"commerceSuiteVersion"[[:space:]]*:[[:space:]]*"[^"]+"' "$CC_DIR/manifest.json" | head -1 || true)
  [[ -n "$cver" ]] && report "commerceSuiteVersion" "${cver#*: }"
  jver=$(grep -oE '"useConfig"[^}]*"java"[^}]*"version"[[:space:]]*:[[:space:]]*"[^"]+"' "$CC_DIR/manifest.json" | grep -oE '"version"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 || true)
  [[ -n "$jver" ]] && report "manifest java.version" "${jver#*: }"
fi

# Platform-level Java (only if platform extracted)
if [[ -f "$CC_DIR/hybris/bin/platform/setantenv.sh" ]]; then
  j=$(grep -oE 'JAVA_HOME=[^ ]+' "$CC_DIR/hybris/bin/platform/setantenv.sh" | head -1 || true)
  [[ -n "$j" ]] && report "platform setantenv" "$j"
fi

hdr "Existing platform extraction (informs in-place vs. copy strategy)"
# An already-extracted bin/platform from a prior commerceSuiteVersion will need to be
# wiped before bootstrapPlatform on the in-place migration path — the gradle plugin
# does not version-upgrade an existing extraction in place.
if [[ -d "$CC_DIR/hybris/bin/platform" ]]; then
  bn="$CC_DIR/hybris/bin/platform/build.number"
  if [[ -f "$bn" ]]; then
    pver=$(grep -E "^version=" "$bn" | head -1 | cut -d= -f2 || true)
    report "platform extracted" "yes — build.number reports version=${pver:-unknown}"
  else
    report "platform extracted" "yes (no build.number — partial extraction?)"
  fi
  if [[ -d "$CC_DIR/hybris/bin/modules" ]]; then
    nmods=$(find "$CC_DIR/hybris/bin/modules" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
    report "modules extracted" "$nmods module dirs"
  fi
  echo "           in-place migration: wipe bin/platform, bin/modules, hybris/config before bootstrapPlatform"
else
  report "platform extracted" "no — fresh project / pre-bootstrap state"
fi

hdr "Spring version (custom extensions)"
spring_dep_found=0
for f in build.gradle dependencies.gradle settings.gradle external-dependencies.xml; do
  [[ -f "$CC_DIR/$f" ]] || continue
  while IFS= read -r l; do
    [[ -z "$l" ]] && continue
    report "  dep ($f)" "$l"
    spring_dep_found=1
  done < <(grep -inE "spring[-._](framework|core|context|webmvc|security)[^/]*[0-9]+\.[0-9]+" "$CC_DIR/$f" 2>/dev/null | head -5)
done
[[ "$spring_dep_found" -eq 0 ]] && report "spring deps" "none in custom build files (transitive from platform)"

# Scan for javax.servlet imports in custom extensions only — implies pre-Jakarta world
SCAN_ROOT="$CC_DIR/hybris/bin/custom"
[[ -d "$SCAN_ROOT" ]] || SCAN_ROOT="$CC_DIR"
if command -v rg >/dev/null 2>&1; then
  jv=$(rg -l --glob "*.java" 'import javax\.(servlet|persistence|validation)' "$SCAN_ROOT" 2>/dev/null | wc -l | tr -d ' ')
else
  jv=$(grep -rl --include="*.java" 'import javax.servlet' "$SCAN_ROOT" 2>/dev/null | wc -l | tr -d ' ')
fi
report "files with javax.* imports" "$jv (under $SCAN_ROOT)"

hdr "Custom extensions"
LE_DEV="$CC_DIR/dev-config/localextensions.xml"
LE_GEN="$CC_DIR/hybris/config/localextensions.xml"
LE_FILE=""
if [[ -f "$LE_DEV" ]]; then
  LE_FILE="$LE_DEV"
elif [[ -f "$LE_GEN" ]]; then
  LE_FILE="$LE_GEN"
fi
if [[ -n "$LE_FILE" ]]; then
  report "localextensions" "$LE_FILE"
  ext_count=$(grep -cE '<extension[[:space:]]+name=' "$LE_FILE" 2>/dev/null || echo 0)
  report "extension count" "$ext_count"
  # Custom extensions usually live under hybris/bin/custom/
  if [[ -d "$CC_DIR/hybris/bin/custom" ]]; then
    custom_dirs=$(find "$CC_DIR/hybris/bin/custom" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')
    report "custom/ extension dirs" "$custom_dirs"
    find "$CC_DIR/hybris/bin/custom" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | while read -r d; do
      echo "           - $(basename "$d")"
    done
  fi
else
  report "localextensions" "not found (checked $LE_DEV and $LE_GEN)"
fi

hdr "Deprecated extensions (blockers)"
blockers=""
for pat in cockpit amazoncloud; do
  for src in "$LE_FILE" "$CC_DIR/manifest.json"; do
    [[ -z "$src" || ! -f "$src" ]] && continue
    if grep -qE "\b${pat}\w*\b" "$src" 2>/dev/null; then
      blockers="$blockers $pat($(basename $(dirname "$src"))/$(basename "$src"))"
    fi
  done
done
if [[ -n "$blockers" ]]; then
  report "⚠ deprecated ext" "$blockers"
else
  report "deprecated ext" "none detected"
fi

hdr "OAuth / OCC footprint"
SCAN_ROOT_OAUTH="$CC_DIR/hybris/bin/custom"
[[ -d "$SCAN_ROOT_OAUTH" ]] || SCAN_ROOT_OAUTH="$CC_DIR"
oauth_hits=$(grep -rlE "OAuth2RestTemplate|@EnableAuthorizationServer|@Required\b" --include="*.java" "$SCAN_ROOT_OAUTH" 2>/dev/null | head -10)
if [[ -n "$oauth_hits" ]]; then
  n=$(echo "$oauth_hits" | wc -l | tr -d ' ')
  report "OAuth2RestTemplate/@Required hits" "$n files"
  if [[ "$n" -gt 0 && "$n" -le 5 ]]; then
    echo "$oauth_hits" | sed 's/^/   - /'
  fi
else
  report "OAuth2RestTemplate/@Required hits" "0 files"
fi

# Any *-oauth*.xml bean definitions
oauth_xmls=$(find "$CC_DIR" -name "*-spring-oauth*.xml" -o -name "*oauth2*.xml" 2>/dev/null | head -10)
if [[ -n "$oauth_xmls" ]]; then
  report "oauth spring xml" "$(echo "$oauth_xmls" | wc -l | tr -d ' ') files"
fi

# Legacy oauth2 extension declaration check (known-incidents #0)
if [[ -n "$LE_FILE" ]] && grep -qE 'extension[[:space:]]+name="oauth2"' "$LE_FILE" 2>/dev/null; then
  report "⚠ legacy oauth2 ext" "DECLARED in $(basename "$LE_FILE") — must replace with oauth2commons + authorizationserver + resourceserver"
fi

hdr "Tomcat customization"
t=$(find "$CC_DIR" -name "server.xml" -path "*tomcat*" 2>/dev/null | head -5)
if [[ -n "$t" ]]; then
  report "server.xml" "$(echo "$t" | head -1)"
else
  report "server.xml" "none found (using platform defaults)"
fi

hdr "Build tooling"
if [[ -f "$CC_DIR/gradlew" ]]; then
  gv=$(grep -E "distributionUrl" "$CC_DIR/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1 || true)
  [[ -n "$gv" ]] && report "gradle wrapper" "$gv"
fi

# SAP Commerce uses platform-bundled ant, activated via setantenv.sh.
if [[ -f "$CC_DIR/hybris/bin/platform/setantenv.sh" ]]; then
  antver=$(cd "$CC_DIR/hybris/bin/platform" && . ./setantenv.sh >/dev/null 2>&1 && ant -version 2>&1 | head -1 || echo "not resolvable — check setantenv.sh output")
  report "platform ant" "$antver"
  echo "           activate: cd core-customize/hybris/bin/platform && . ./setantenv.sh"
else
  report "platform ant" "setantenv.sh not found — bootstrapPlatform has not run yet (no platform extracted)"
fi
if command -v ant >/dev/null 2>&1; then
  report "system ant (advisory)" "$(ant -version 2>&1 | head -1) — NOT used by Commerce builds"
fi

hdr "Commerce ZIPs in core-customize/dependencies/"
suite_zip=$(ls "$CC_DIR/dependencies/"hybris-commerce-suite-*.zip 2>/dev/null | head -1)
if [[ -n "$suite_zip" ]]; then
  report "suite zip" "$(basename "$suite_zip")"
else
  report "⚠ suite zip" "MISSING — bootstrapPlatform will fail. Place hybris-commerce-suite-<target>.zip in $CC_DIR/dependencies/"
fi
int_zip=$(ls "$CC_DIR/dependencies/"hybris-commerce-integrations-*.zip 2>/dev/null | head -1)
if [[ -n "$int_zip" ]]; then
  report "integrations zip" "$(basename "$int_zip") (verify it matches target release)"
else
  report "integrations zip" "absent — OK to proceed; Phase F OCC tests will need a matching pack later"
fi

hdr "Summary"
echo "Feed this report into the skill's intake step (Phase 1 Step 3)."
echo "Target state: SapMachine 21 + Spring 6.2.10 + Tomcat 10.1 (Update Release 2211-jdk21.1)."
