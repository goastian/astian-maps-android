plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.astian.suite.maps.core"
    testOptions {
        // The on-demand obf harnesses (ObfSpeedLimitProbeTest, ObfRoadNamesProbeTest) run the real
        // engine on the JVM, and its android.util.Log lines would otherwise throw "not mocked".
        unitTests.isReturnDefaultValues = true
    }
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.rhino.runtime)
    // OsmAnd obf routing engine (ObfRouteEngine): the router + binary obf reader as plain Java
    // jars, vendored from OsmAndMapCreator's lib (GPLv3, same license as Astian Maps). Gitignored like
    // the sherpa AAR - CI fetches them from the `obf-runtime` infra release; locally copy them in
    // (see CLAUDE.md). Everything else the router needs is already on Android (org.json,
    // kotlin-stdlib) or in this module (kotlinx-serialization). commons-logging + kxml2 are the
    // two desktop assumptions Android does NOT satisfy: OsmAnd logs through commons-logging and
    // instantiates org.kxml2.io.KXmlParser directly (present on a desktop classpath, not visible
    // to apps on modern Android) - both device-caught on the release canary, 2026-07-23.
    // kxml2-astian.jar is upstream kxml2 2.3.0 with its bundled org/xmlpull/** REMOVED - the stock
    // Maven jar duplicates the platform's XmlPullParser interfaces and R8 hard-fails on the
    // library/program split ("Library class android.content.res.XmlResourceParser implements
    // program class org.xmlpull.v1.XmlPullParser").
    // osmand-java.jar comes through :osmand-shaded (its protobuf relocated, so Cronet's can coexist).
    implementation(project(mapOf("path" to ":osmand-shaded", "configuration" to "shadow")))
    implementation(files("libs/osmand-shared-jvm.jar", "libs/gnu-trove-osmand.jar", "libs/kxml2-astian.jar"))
    implementation("commons-logging:commons-logging:1.4.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // The real org.json for the on-demand obf harness (ObfSpeedLimitProbeTest): the engine reads
    // its region index with JSONArray, and Android's unit-test jar only stubs it.
    testImplementation("org.json:json:20240303")
}

// Forward the trip-audit harness property into the TEST JVM (see NavReplayTest.auditSharedTripLog):
// `-DastianTrip=…` on the command line sets a GRADLE-daemon property, which the forked test JVM does
// NOT inherit — without this the documented audit command silently skipped the test every time.
tasks.withType<Test>().configureEach {
    System.getProperty("astianTrip")?.let { systemProperty("astianTrip", it) }
    System.getProperty("astianSeg")?.let { systemProperty("astianSeg", it) }
    System.getProperty("astianProbe")?.let { systemProperty("astianProbe", it) }
    System.getProperty("astianObf")?.let { systemProperty("astianObf", it) }
    System.getProperty("astianCells")?.let { systemProperty("astianCells", it) }
    System.getProperty("astianLive")?.let { systemProperty("astianLive", it) }
    System.getProperty("astianStudy")?.let { systemProperty("astianStudy", it) }
    System.getProperty("astianOne")?.let { systemProperty("astianOne", it) }
    System.getProperty("astianLines")?.let { systemProperty("astianLines", it) }
    System.getProperty("astianNudge")?.let { systemProperty("astianNudge", it) }
    System.getProperty("astianFeedProbe")?.let { systemProperty("astianFeedProbe", it) }
    // Painted-roads bake (PaintedRoadsBakeTest): a whole state's streets need a big heap.
    System.getProperty("astianPaintIn")?.let { systemProperty("astianPaintIn", it); maxHeapSize = "20g" }
    System.getProperty("astianPaintOut")?.let { systemProperty("astianPaintOut", it) }
}
