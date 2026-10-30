#!/usr/bin/env bash
# Builds the release variant (R8, signed with the debug key unless ASTIAN_KEYSTORE_PATH is set) and
# installs it on the connected device. Until Astian publishes its own data catalogs, the offline
# catalogs, building/place overlays and map fonts are read from the upstream project's published
# data, so offline regions and overlays can be tried on a phone. Drop DATA_BASE once the Astian
# catalogs exist and AstianLinks points at them.
set -euo pipefail
cd "$(dirname "$0")/.."

DATA_BASE="${ASTIAN_DEV_DATA_BASE:-https://github.com/PimpinPumpkin/Vela/releases/download}"
FONTS="${ASTIAN_DEV_FONTS_URL:-https://pimpinpumpkin.github.io/Vela/fonts}"

./gradlew :app:assembleRelease -PplayStore=false \
  -PobfManifestUrl="$DATA_BASE/obf-regions/obf-manifest.json" \
  -PoverlayManifestUrl="$DATA_BASE/building-overlays/building-overlay-manifest.json" \
  -PmaxspeedManifestUrl="$DATA_BASE/maxspeed-overlays/maxspeed-overlay-manifest.json" \
  -PaddressManifestUrl="$DATA_BASE/address-overlays/address-overlay-manifest.json" \
  -PplacesManifestUrl="$DATA_BASE/places-overlays/places-overlay-manifest.json" \
  -PcellsManifestUrl="$DATA_BASE/grid-cells/cells-manifest.json" \
  -PbasemapManifestUrl="$DATA_BASE/basemap-tiles/basemap-manifest.json" \
  -PworldBasemapUrl="$DATA_BASE/basemap-tiles/basemap-world.pmtiles" \
  -PpoiPackManifestUrl="$DATA_BASE/poi-packs/poi-pack-manifest.json" \
  -PflockManifestUrl="$DATA_BASE/flock-cameras/flock-manifest.json" \
  -ProadFeaturesManifestUrl="$DATA_BASE/road-features/road-features-manifest.json" \
  -PmapFontsUrl="$FONTS" \
  "$@"

apk=$(ls -t app/build/outputs/apk/release/*.apk | head -1)
adb install -r "$apk"
