#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

ci_banner "job assemble"
cd "$REPO_ROOT"
ci_overlay_versioned_source
ci_prepare_android_sdk
[[ -x ./gradlew ]] || ci_die "./gradlew is missing or not executable"
log_step "./gradlew :nshiftui:assembleRelease :nshiftui-ksp:jar"
./gradlew :nshiftui:assembleRelease :nshiftui-ksp:jar --stacktrace

log_step "stage release AAR"
mkdir -p build/outputs/aar
AAR_SRC="$(find nshiftui/build/outputs/aar -name '*-release.aar' -print -quit || true)"
if [[ -z "$AAR_SRC" ]]; then
  log_error "no *-release.aar under nshiftui/build/outputs/aar"
  find nshiftui/build/outputs -name '*.aar' -print || true
  ci_die "assembleRelease did not produce an AAR"
fi
cp "$AAR_SRC" build/outputs/aar/NShiftUI-Android-release.aar
log_ok "AAR build/outputs/aar/NShiftUI-Android-release.aar"

log_step "stage KSP jar"
mkdir -p build/outputs/ksp
JAR_SRC="$(find nshiftui-ksp/build/libs -name 'nshiftui-ksp-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' -print -quit || true)"
if [[ -z "$JAR_SRC" ]]; then
  log_error "no nshiftui-ksp-*.jar under nshiftui-ksp/build/libs"
  ls -la nshiftui-ksp/build/libs || true
  ci_die ":nshiftui-ksp:jar did not produce a JAR"
fi
cp "$JAR_SRC" build/outputs/ksp/nshiftui-ksp.jar
log_ok "JAR build/outputs/ksp/nshiftui-ksp.jar"
