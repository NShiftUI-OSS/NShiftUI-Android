#!/usr/bin/env bash
# Shared by test/assemble/commit (not a GitHub job).
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/helpers.sh"

ci_overlay_versioned_source() {
  log_step "overlay versioned-source"
  local src=".ci-version"
  if [[ ! -d "$src" ]]; then
    log_info "no versioned-source overlay; using checkout as-is"
    return 0
  fi
  [[ -f "${src}/NShiftUIVersion.kt" ]] || ci_die "missing ${src}/NShiftUIVersion.kt"
  [[ -f "${src}/nshiftui.build.gradle.kts" ]] || ci_die "missing ${src}/nshiftui.build.gradle.kts"
  [[ -f "${src}/nshiftui-ksp.build.gradle.kts" ]] || ci_die "missing ${src}/nshiftui-ksp.build.gradle.kts"
  [[ -f "${src}/NShiftUIVersionTests.kt" ]] || ci_die "missing ${src}/NShiftUIVersionTests.kt"
  cp "${src}/NShiftUIVersion.kt" nshiftui/src/main/kotlin/com/nshiftui/NShiftUIVersion.kt
  cp "${src}/nshiftui.build.gradle.kts" nshiftui/build.gradle.kts
  cp "${src}/nshiftui-ksp.build.gradle.kts" nshiftui-ksp/build.gradle.kts
  cp "${src}/NShiftUIVersionTests.kt" nshiftui/src/test/kotlin/com/nshiftui/NShiftUIVersionTests.kt
  log_ok "overlaid version files onto checkout"
}

ci_prepare_android_sdk() {
  log_step "Android SDK"
  if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
      ANDROID_HOME="$ANDROID_SDK_ROOT"
    elif [[ -d /usr/local/lib/android/sdk ]]; then
      ANDROID_HOME=/usr/local/lib/android/sdk
    else
      ci_die "ANDROID_HOME is unset and no SDK was found at /usr/local/lib/android/sdk"
    fi
    export ANDROID_HOME
  fi
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
  printf "sdk.dir=%s\n" "$ANDROID_HOME" > local.properties
  log_info "ANDROID_HOME=${ANDROID_HOME}"

  local sdkmanager=""
  if command -v sdkmanager >/dev/null 2>&1; then
    sdkmanager="$(command -v sdkmanager)"
  elif [[ -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]]; then
    sdkmanager="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
  else
    sdkmanager="$(find "$ANDROID_HOME/cmdline-tools" -type f -name sdkmanager -print -quit 2>/dev/null || true)"
  fi
  [[ -n "$sdkmanager" && -x "$sdkmanager" ]] || ci_die "sdkmanager not found under ${ANDROID_HOME}"
  log_info "sdkmanager=${sdkmanager}"

  log_step "accept SDK licenses (non-interactive)"
  set +o pipefail
  yes "y" | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  set -o pipefail

  # API 37 is published as platforms;android-37.0 (directory platforms/android-37.0), not android-37.
  ci_install_sdk_package "$sdkmanager" "platforms;android-37.0" \
    "$ANDROID_HOME/platforms/android-37.0" "$ANDROID_HOME/platforms/android-37"
  ci_install_sdk_package "$sdkmanager" "build-tools;36.0.0" \
    "$ANDROID_HOME/build-tools/36.0.0"

  if [[ -d "$ANDROID_HOME/platforms/android-37.0" && ! -e "$ANDROID_HOME/platforms/android-37" ]]; then
    log_info "symlink platforms/android-37 -> android-37.0"
    ln -s android-37.0 "$ANDROID_HOME/platforms/android-37"
  fi
  log_ok "Android SDK ready"
}

ci_install_sdk_package() {
  local sdkmanager="$1"
  local pkg="$2"
  shift 2
  local marker
  for marker in "$@"; do
    if [[ -e "$marker" ]]; then
      log_ok "already present: ${pkg} (${marker})"
      return 0
    fi
  done
  log_step "install ${pkg}"
  if ! "$sdkmanager" --sdk_root="$ANDROID_HOME" "$pkg"; then
    log_error "sdkmanager failed for ${pkg}"
    "$sdkmanager" --sdk_root="$ANDROID_HOME" --list 2>/dev/null | grep -F "${pkg%%;*}" || true
    ci_die "failed to install ${pkg}"
  fi
  log_ok "installed ${pkg}"
}
