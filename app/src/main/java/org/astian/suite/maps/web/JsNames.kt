package org.astian.suite.maps.web

/**
 * The names our JavaScript bridges carry inside Google's pages, random for each process
 * (2026-09-23). `addJavascriptInterface` puts the object on the page's `window`, where Google's own
 * scripts can read it, so a fixed "AstianBridge" or "AstianPanel" named the app to anyone who looked.
 * Scripts keep the readable names in source; [of] swaps them in right before a script runs.
 */
object JsNames {
    private fun random(): String = "_" + (1..10).map { "abcdefghijklmnopqrstuvwxyz"[kotlin.random.Random.nextInt(26)] }.joinToString("")

    /** The hidden fetchers' result channel (HiddenWebView), written `AstianBridge` in scripts. */
    val bridge: String = random()
    /** The full-screen reviews page's channel (ReviewsPanel), written `AstianPanel` in scripts. */
    val panel: String = random()

    fun of(js: String): String = js.replace("AstianBridge", bridge).replace("AstianPanel", panel)
}
