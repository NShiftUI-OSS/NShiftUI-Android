#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

ci_banner "job test"
cd "$REPO_ROOT"
ci_overlay_versioned_source
ci_prepare_android_sdk
[[ -x ./gradlew ]] || ci_die "./gradlew is missing or not executable"
log_step "./gradlew test"
./gradlew test --stacktrace
log_ok "tests passed"
