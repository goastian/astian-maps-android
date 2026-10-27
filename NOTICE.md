# Notices

Astian Maps for Android is a derivative of **Vela** (https://github.com/PimpinPumpkin/Vela) by
PimpinPumpkin and its contributors, used under the GNU General Public License v3.0 (`GPL-3.0`,
see [LICENSE](LICENSE)). The original copyright and license notices stay with the files they came
from, and this application remains licensed under the GPL-3.0 as a whole. The rest of the
monorepo (web client, API, packages) is a separate work under Apache-2.0.

Renamed, restyled or changed by Astian: the package (`org.astian.suite.maps`), the name, the
logo and the project links. Everything else is Vela's code unless a file says otherwise.

## Data and components

- Map data from OpenStreetMap contributors, available under the Open Database License
  (https://www.openstreetmap.org/copyright).
- Vector basemap in the OpenMapTiles schema; rendering by MapLibre Native (BSD-2-Clause).
- Places from Overture Maps (CDLA Permissive 2.0 / ODbL, per theme) and AllThePlaces.
- Offline routing by the OsmAnd routing library (GPL-3.0).
- Network stack: Cronet from Chromium (BSD-3-Clause). On-device voice: sherpa-onnx
  (Apache-2.0) with its models under their own licenses.
- Google Sans Flex and the Roboto and Noto glyph sets under the SIL Open Font License 1.1.

The full per-dependency list is the Gradle dependency tree of `:app` and `:core`; the
per-request behavior is described in [PRIVACY.md](PRIVACY.md).
