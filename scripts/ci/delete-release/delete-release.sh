#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"

ci_banner "job delete-release"
require_env TAG TAG_BARE GH_TOKEN GITHUB_REPOSITORY
require_cmd gh

deleted=false
while IFS= read -r name; do
  log_step "GitHub Release ${name}"
  if gh release view "$name" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
    gh release delete "$name" --yes --repo "$GITHUB_REPOSITORY"
    log_ok "deleted GitHub Release ${name}"
    deleted=true
  else
    log_info "no GitHub Release named ${name}"
  fi
done < <(ci_each_tag_name)

ci_output deleted "$deleted"
if [[ "$deleted" == true ]]; then
  log_ok "release cleanup done"
else
  log_info "no GitHub Release matched ${TAG} or ${TAG_BARE}; tag job will check git tags"
fi
