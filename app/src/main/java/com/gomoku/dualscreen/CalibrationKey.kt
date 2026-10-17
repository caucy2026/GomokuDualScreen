package com.gomoku.dualscreen

/**
 * Pure calibration key helpers. Firmware differences must not make the app uncalibratable:
 * a build that does not expose vendor.nl.screen_mode gets the explicit unknown marker "?"
 * and therefore its own calibration entry instead of a blocked key containing "null".
 */
object CalibrationKey {
    private val KNOWN_MODES = listOf("-1", "0", "1")

    fun shapeOf(screenMode: String?): String = if (screenMode in KNOWN_MODES) screenMode!! else "?"

    fun shapeKnown(screenMode: String?): Boolean = screenMode in KNOWN_MODES

    fun masterOf(key: String): String = key.substringBefore(":")

    /** A state can be calibrated once display.master is readable; vendor.nl.screen_mode may be missing. */
    fun saveable(key: String): Boolean = key.isNotBlank() && masterOf(key) in listOf("0", "1")

    fun of(master: String?, screenMode: String?, panels: List<Pair<Int, Int>>): String =
        "${master ?: "?"}:${shapeOf(screenMode)}:" +
            panels.joinToString(",") { "${it.first}=${it.second}" }
}
