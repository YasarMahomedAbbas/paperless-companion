#!/usr/bin/env bash
# Run a command in the build container, as the host user so output files stay ours.
#   ./build.sh                      -> ./gradlew assembleDebug
#   ./build.sh ./gradlew test       -> any other command
#   ./build.sh install              -> build, then adb install on the host (phone over USB)
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=paperless-companion-build
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/paperless-companion"
mkdir -p "$CACHE/gradle" "$CACHE/android"

docker build -q -t "$IMAGE" docker > /dev/null

install=false
if [[ "${1:-}" == install ]]; then install=true; shift; fi
[[ $# -eq 0 ]] && set -- ./gradlew assembleDebug

docker run --rm -t \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/cache/gradle \
  -e ANDROID_USER_HOME=/cache/android \
  -v "$PWD:/src" \
  -v "$CACHE:/cache" \
  "$IMAGE" "$@"

if $install; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk
fi
