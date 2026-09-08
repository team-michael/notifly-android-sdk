#!/usr/bin/env bash

set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_dir="$root_dir/notifly-kmp-sdk"
sdk_version="$(sed -n 's/^version=//p' "$root_dir/gradle.properties")"
stage_dir="$(mktemp -d)"
trap 'rm -rf "$stage_dir"' EXIT

if [[ ! -x "$core_dir/gradlew" ]]; then
  echo "notifly-kmp-sdk submodule is not initialized. Run: git submodule update --init --recursive" >&2
  exit 1
fi

GROUP="com.github.team-michael.notifly-android-sdk" \
VERSION="$sdk_version" \
MAVEN_ROOT_ARTIFACT_ID="core-metadata" \
MAVEN_JVM_ARTIFACT_ID="core" \
"$core_dir/gradlew" \
  -p "$core_dir" \
  clean \
  publishJvmPublicationToMavenLocal \
  "-Dmaven.repo.local=$stage_dir/m2" \
  --no-daemon

"$root_dir/gradlew" \
  -p "$root_dir" \
  :notifly:publishReleasePublicationToMavenLocal \
  "-Dmaven.repo.local=$stage_dir/m2" \
  --no-daemon

core_jar="$stage_dir/m2/com/github/team-michael/notifly-android-sdk/core/$sdk_version/core-$sdk_version.jar"
full_aar="$stage_dir/m2/com/github/team-michael/notifly-android-sdk/$sdk_version/notifly-android-sdk-$sdk_version.aar"
full_pom="$stage_dir/m2/com/github/team-michael/notifly-android-sdk/$sdk_version/notifly-android-sdk-$sdk_version.pom"

test -f "$core_jar"
test -f "$full_aar"
grep -F '<groupId>com.github.team-michael.notifly-android-sdk</groupId>' "$full_pom" >/dev/null
grep -F '<artifactId>core</artifactId>' "$full_pom" >/dev/null
grep -F "<version>$sdk_version</version>" "$full_pom" >/dev/null

echo "Android Core and Full artifacts were packaged at version $sdk_version."
