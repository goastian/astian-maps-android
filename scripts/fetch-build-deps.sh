#!/usr/bin/env bash
# Fetches the prebuilt binaries the Android build needs but the repository does not hold: the
# neural voice runtime, Cronet and OsmAnd's router jars. They are mirrored from the upstream
# project's `*-runtime` releases until Astian publishes the same files under its own releases
# (set ASTIAN_RUNTIME_BASE to point at them).
set -euo pipefail
cd "$(dirname "$0")/.."

BASE="${ASTIAN_RUNTIME_BASE:-https://github.com/PimpinPumpkin/Vela/releases/download}"
CRONET_VERSION=$(sed -n 's/^astian.cronetVersion=//p' gradle.properties)

fetch() {
  local url="$1" dest="$2" min="$3"
  if [ -s "$dest" ] && [ "$(stat -c%s "$dest")" -gt "$min" ]; then return; fi
  mkdir -p "$(dirname "$dest")"
  curl -fSL --retry 3 -o "$dest" "$url"
  test "$(stat -c%s "$dest")" -gt "$min"
}

fetch "$BASE/tts-runtime/sherpa-onnx-1.13.3.aar" app/libs/sherpa-onnx-1.13.3.aar 50000000
fetch "$BASE/cronet-runtime/cronet-$CRONET_VERSION.aar" "app/libs/cronet-$CRONET_VERSION.aar" 1000000
fetch "$BASE/obf-runtime/osmand-java.jar" core/libs/osmand-java.jar 10000000
fetch "$BASE/obf-runtime/osmand-shared-jvm.jar" core/libs/osmand-shared-jvm.jar 1000000
fetch "$BASE/obf-runtime/gnu-trove-osmand.jar" core/libs/gnu-trove-osmand.jar 100000
# The release asset keeps the upstream file name; the build reads it as kxml2-astian.jar.
fetch "$BASE/obf-runtime/kxml2-vela.jar" core/libs/kxml2-astian.jar 10000
