#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"

ci_banner "job release"
require_env TAG VERSION BRANCH GH_TOKEN
require_cmd gh find
cd "$REPO_ROOT"

DIST="${AAR_DIR:-dist}"
log_step "find artifacts in ${DIST}"
[[ -d "$DIST" ]] || ci_die "missing artifact download dir ${DIST}"
AAR="$(find "$DIST" -name '*.aar' -print -quit || true)"
JAR="$(find "$DIST" -name '*.jar' -print -quit || true)"
[[ -n "$AAR" ]] || ci_die "no .aar in ${DIST}"
[[ -n "$JAR" ]] || ci_die "no .jar in ${DIST}"
log_ok "attaching ${AAR}"
log_ok "attaching ${JAR}"

log_step "gh release create ${TAG}"
gh release create "$TAG" "$AAR" "$JAR" \
  --repo "${GITHUB_REPOSITORY:?}" \
  --title "$TAG" \
  --notes "AAR and KSP ${VERSION} built from branch \`${BRANCH}\`."
log_ok "GitHub Release ${TAG} published"
