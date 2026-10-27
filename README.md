<div align="center">

<img src="docs/logo.svg" width="120" alt="Astian Maps logo">

# Astian Maps for Android

Native maps, search and turn-by-turn navigation with a community status report. Offline regions, on-device voice, Android Auto.

</div>

Package `org.astian.suite.maps`. Kotlin, Jetpack Compose and MapLibre Native, with no Google SDK
and no Play Services. It is a derivative of Vela (GPL-3.0); see [NOTICE.md](NOTICE.md) for the
attribution and what Astian changed.

## Screenshots

All screenshots use the built-in location simulator, in Davis, California.

### Map, places and search

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-map.png" width="200" alt="Dark map with quick categories"></td>
    <td align="center"><img src="docs/screenshots/02-search.png" width="200" alt="Search results with ratings on the map"></td>
    <td align="center"><img src="docs/screenshots/03-place.png" width="200" alt="Place page with photos, rating and actions"></td>
    <td align="center"><img src="docs/screenshots/09-place-light.png" width="200" alt="Place page in the light theme"></td>
  </tr>
  <tr>
    <td align="center">Map and quick categories</td>
    <td align="center">Search</td>
    <td align="center">Place page</td>
    <td align="center">Light theme</td>
  </tr>
</table>

### Community reports

Report traffic, police, a crash, a hazard, a pothole, roadworks, a closure, weather or a camera
in two taps. Reports reach every phone and the [web map](https://github.com/goastian/astian-maps-web)
as they are sent; the web only shows them. No account, no name.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/12-community-map.png" width="200" alt="Map with community report pins and a jam painted on the road"></td>
    <td align="center"><img src="docs/screenshots/13-community-report.png" width="200" alt="Card for a heavy traffic report with Still slow and Moving now"></td>
    <td align="center"><img src="docs/screenshots/14-report-picker.png" width="200" alt="Report picker with the nine report types"></td>
    <td align="center"><img src="docs/screenshots/15-community-dark.png" width="200" alt="Dark map showing every report type, a closure and roadworks"></td>
  </tr>
  <tr>
    <td align="center">Reports on the map</td>
    <td align="center">Confirm a report</td>
    <td align="center">Make a report</td>
    <td align="center">All types, dark map</td>
  </tr>
</table>

### Directions and navigation

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/17-drive-directions.png" width="200" alt="Drive directions with the transport mode tabs"></td>
    <td align="center"><img src="docs/screenshots/04-directions.png" width="200" alt="Route with alternatives and traffic"></td>
    <td align="center"><img src="docs/screenshots/05-navigation.png" width="200" alt="Turn-by-turn navigation with speed limit"></td>
    <td align="center"><img src="docs/screenshots/18-navigation-alert.png" width="200" alt="Navigation with a police report ahead"></td>
  </tr>
  <tr>
    <td align="center">Drive, transit, walk, bike</td>
    <td align="center">Alternatives</td>
    <td align="center">Turn-by-turn</td>
    <td align="center">Report ahead</td>
  </tr>
</table>

### Transit, layers and themes

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/06-transit.png" width="200" alt="Transit route with departure options"></td>
    <td align="center"><img src="docs/screenshots/07-bus-stop.png" width="200" alt="Bus stop page with upcoming departures"></td>
    <td align="center"><img src="docs/screenshots/11-stop-list.png" width="200" alt="Stop list of a transit line with scheduled times"></td>
    <td align="center"><img src="docs/screenshots/16-layers.png" width="200" alt="Layers panel with map types, details and place icons"></td>
  </tr>
  <tr>
    <td align="center">Transit routes</td>
    <td align="center">Stop departures</td>
    <td align="center">Line stops</td>
    <td align="center">Map types and layers</td>
  </tr>
</table>

## What it does

[FEATURES.md](FEATURES.md) lists every feature in one line each; [SPEC.md](SPEC.md) and
[docs/book/](docs/book/README.md) explain how they work. In short: an open vector map, place
pages, search, driving, walking, cycling and transit routes, durable turn-by-turn navigation
with lane guidance and spoken directions, offline map/search/routing by region, saved places
and lists, and an Android Auto session.

How the app reaches the network, request by request, is in [PRIVACY.md](PRIVACY.md).

## Build and install

```bash
scripts/fetch-build-deps.sh       # once: prebuilt runtime binaries that are not in git
./gradlew :app:assembleRelease    # R8 build; signed with the debug key unless ASTIAN_KEYSTORE_PATH is set
scripts/dev-install.sh            # same build plus adb install, with the dev data catalogs
```

[docs/BUILDING.md](docs/BUILDING.md) has the details, the tests and signing.

## Where it fits in the monorepo

`apps/android` is the mobile client. The shared contracts, the API (`services/api`) and the web
client ([goastian/astian-maps-web](https://github.com/goastian/astian-maps-web)) live beside it; the plan that orders the work is [docs/plan_maps.md](../../docs/plan_maps.md).

## Project links

Every address the app has of its own project (repository, releases, data catalogs, site) is in
`core/src/main/java/org/astian/suite/maps/core/config/AstianLinks.kt`. Until Astian publishes
its own data catalogs, `scripts/dev-install.sh` points a build at the upstream project's.

## License

GPL-3.0, see [LICENSE](LICENSE).
