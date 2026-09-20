#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"

ci_banner "job bump"
require_env VERSION
cd "$REPO_ROOT"

VERSION_KT="nshiftui/src/main/kotlin/com/nshiftui/NShiftUIVersion.kt"
NSHIFTUI_GRADLE="nshiftui/build.gradle.kts"
KSP_GRADLE="nshiftui-ksp/build.gradle.kts"
VERSION_TESTS="nshiftui/src/test/kotlin/com/nshiftui/NShiftUIVersionTests.kt"

log_step "patch working tree (no commit)"
ci_sed_inplace "s/const val current = \"[^\"]+\"/const val current = \"${VERSION}\"/" "$VERSION_KT"
ci_sed_inplace "s/^version = \"[^\"]+\"/version = \"${VERSION}\"/" "$NSHIFTUI_GRADLE"
ci_sed_inplace "s/^version = \"[^\"]+\"/version = \"${VERSION}\"/" "$KSP_GRADLE"
ci_sed_inplace "s/NShiftUIVersion.current shouldBe \"[^\"]+\"/NShiftUIVersion.current shouldBe \"${VERSION}\"/" "$VERSION_TESTS"
grep -F "const val current = \"${VERSION}\"" "$VERSION_KT" >/dev/null \
  || ci_die "failed to patch ${VERSION_KT}"
grep -F "version = \"${VERSION}\"" "$NSHIFTUI_GRADLE" >/dev/null \
  || ci_die "failed to patch ${NSHIFTUI_GRADLE}"
grep -F "version = \"${VERSION}\"" "$KSP_GRADLE" >/dev/null \
  || ci_die "failed to patch ${KSP_GRADLE}"
grep -F "NShiftUIVersion.current shouldBe \"${VERSION}\"" "$VERSION_TESTS" >/dev/null \
  || ci_die "failed to patch ${VERSION_TESTS}"
log_ok "patched ${VERSION_KT}"
log_ok "patched ${NSHIFTUI_GRADLE}"
log_ok "patched ${KSP_GRADLE}"
log_ok "patched ${VERSION_TESTS}"

log_step "stage artifact files"
mkdir -p .ci-version
cp "$VERSION_KT" .ci-version/NShiftUIVersion.kt
cp "$NSHIFTUI_GRADLE" .ci-version/nshiftui.build.gradle.kts
cp "$KSP_GRADLE" .ci-version/nshiftui-ksp.build.gradle.kts
cp "$VERSION_TESTS" .ci-version/NShiftUIVersionTests.kt
log_ok "artifact dir .ci-version ready"
