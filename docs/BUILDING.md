# Building and running Astian Maps

To install the app, use the Obtainium or F-Droid badge on the [README](../README.md) or an
APK from [Releases](https://github.com/goastian/astian-maps-android/releases). This page is for building
from source.

## Build and run

Standard Android toolchain (JDK 17; the Gradle wrapper fetches Gradle 9.8.0 and AGP 9.4.1).

Three pieces are not in git and have to be fetched once before the first build. They are
prebuilt binaries with no Maven artifact, mirrored from the upstream project's `*-runtime`
releases until Astian publishes its own copies:

```bash
scripts/fetch-build-deps.sh   # sherpa-onnx (about 57 MB), Cronet and OsmAnd's router jars
```

Set `ASTIAN_RUNTIME_BASE` to fetch them from another release base URL.

```bash
# debug build (compile check / local install)
./gradlew :app:assembleDebug

# the build that goes on a phone (R8 and resource shrinking).
# A debug build drops frames on the map and reads as a performance bug.
./gradlew :app:assembleRelease

# unit tests for the pure logic in :core (parsers, nav engine, routing, the name rules)
./gradlew :core:test
```

Release signing comes from environment variables (`ASTIAN_KEYSTORE_PATH`,
`ASTIAN_KEYSTORE_PASSWORD`, `ASTIAN_KEY_ALIAS`). Without them a local build is signed with the
debug keystore, so `adb install` still works. Do not hand out a local build with a
`-PappVersionCode` above the release line: the next real release then looks like a downgrade.
`-PappId=org.astian.suite.maps.dev` builds a copy that installs beside the real app.

## What CI does

Every push to `main` or `canary` builds and tests. A push to `canary` replaces the rolling
canary build. A daily job publishes a signed nightly prerelease (`v0.5.<run>`) when `main` has
moved, and a weekly job (Mondays) promotes the newest nightly to stable. The F-Droid repository
index is rebuilt after both. Docs-only pushes skip CI. [Chapter 12 of the book](book/12-releases.md)
has the details.

## Architecture

Two Gradle modules. `:core` has no interface code: the models, the requests to Google and
their parsers, the open routers and the on-phone router, the navigation engine, and the remote
settings. `:app` is the Compose interface over MapLibre. `core/data/MapDataSource` is the seam
between them. The module tree is in [`SPEC.md`](../SPEC.md) section 2.

Toolchain: JDK 17, Gradle 9.8, AGP 9.4, Kotlin 2.4. The app compiles against SDK 37, targets
35 and runs on Android 8 (SDK 26) and up.
