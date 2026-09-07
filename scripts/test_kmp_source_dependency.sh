#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUBMODULE_DIR="$ROOT_DIR/notifly-kmp-sdk"
CI_WORKFLOW="$ROOT_DIR/.github/workflows/ci.yml"
BUMP_WORKFLOW="$ROOT_DIR/.github/workflows/bump-kmp-submodule.yml"

expected_url="https://github.com/notifly-tech/notifly-kmp-sdk.git"
actual_url="$(git -C "$ROOT_DIR" config -f .gitmodules --get submodule.notifly-kmp-sdk.url)"
if [[ "$actual_url" != "$expected_url" ]]; then
  echo "Expected KMP submodule URL $expected_url, got $actual_url" >&2
  exit 1
fi

if [[ ! -f "$SUBMODULE_DIR/gradlew" ]]; then
  echo "KMP submodule is not initialized" >&2
  exit 1
fi

if ! git -C "$SUBMODULE_DIR" describe --exact-match --tags HEAD >/dev/null 2>&1; then
  echo "KMP submodule must point to a tagged commit" >&2
  exit 1
fi

grep -F "submodules: recursive" "$CI_WORKFLOW" >/dev/null
grep -F "workflow_dispatch:" "$BUMP_WORKFLOW" >/dev/null
grep -F "https://github.com/notifly-tech/notifly-kmp-sdk.git" "$BUMP_WORKFLOW" >/dev/null
grep -F "gh workflow run ci.yml" "$BUMP_WORKFLOW" >/dev/null

if [[ -z "${JAVA_HOME:-}" ]] && command -v brew >/dev/null 2>&1; then
  brew_prefix="$(brew --prefix openjdk@17 2>/dev/null || true)"
  java_home="$brew_prefix/libexec/openjdk.jdk/Contents/Home"
  if [[ -x "$java_home/bin/java" ]]; then
    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

if ! java -version >/dev/null 2>&1; then
  echo "Java 17 or newer is required to verify the Android dependency graph" >&2
  exit 1
fi

dependency_report="$(mktemp)"
trap 'rm -f "$dependency_report"' EXIT

"$ROOT_DIR/gradlew" :notifly:dependencies \
  --configuration debugRuntimeClasspath \
  --console=plain >"$dependency_report"

if ! grep -F "project :notifly-kmp-sdk:kmp" "$dependency_report" >/dev/null; then
  echo "Android SDK does not compile against the KMP submodule project" >&2
  exit 1
fi

echo "Android uses the tagged KMP submodule project."
