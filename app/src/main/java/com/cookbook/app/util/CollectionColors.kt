package com.cookbook.app.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

/**
 * Farbe einer Sammlung — für Marker-Punkt, Badge-Text und Icon-Tile.
 *
 * Die Farbe ist nie eine Fläche: als Tile-Hintergrund wird sie auf 13 % Deckung
 * gesetzt ([tint]). Sammlungen, die nicht in der festen Reihe stehen, bekommen
 * ihren Farbton aus dem Namen gehasht — Helligkeit und Sättigung bleiben auf dem
 * Niveau der Reihe, damit nichts herausfällt.
 */
object CollectionColors {

    /** Deckung der Sammlungsfarbe, wenn sie als Fläche dient. */
    private const val TILE_ALPHA = 33 // 13 % von 255

    private val NAMED: Map<String, Int> = mapOf(
        "auflauf" to 0xFFC2793A.toInt(),
        "auflaeufe" to 0xFFC2793A.toInt(),
        "aufläufe" to 0xFFC2793A.toInt(),
        "fleisch" to 0xFFC55340.toInt(),
        "nudeln" to 0xFFB8862F.toInt(),
        "pasta" to 0xFFB8862F.toInt(),
        "leicht" to 0xFF468F5E.toInt(),
        "salat" to 0xFF468F5E.toInt(),
        "suppen" to 0xFFB96A43.toInt(),
        "suppe" to 0xFFB96A43.toInt(),
        "fisch" to 0xFF4C8DA6.toInt(),
        "wok" to 0xFF9A5D9C.toInt()
    )

    /** Farbton-Werte der festen Reihe, damit gehashte Farben dazwischen landen. */
    private const val HASHED_SATURATION = 0.62f
    private const val HASHED_VALUE = 0.72f

    @ColorInt
    fun forName(name: String?): Int {
        if (name.isNullOrBlank()) return 0xFF8E7666.toInt()

        val key = name.trim().lowercase()
        NAMED[key]?.let { return it }
        NAMED.entries.firstOrNull { key.contains(it.key) }?.let { return it.value }

        // Farbton stabil aus dem Namen ableiten
        var hash = 0
        for (char in key) {
            hash = char.code + ((hash shl 5) - hash)
        }
        val hue = ((hash % 360) + 360) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), HASHED_SATURATION, HASHED_VALUE))
    }

    /** Dieselbe Farbe als Fläche — 13 % Deckung, nie voll. */
    @ColorInt
    fun tint(name: String?): Int = ColorUtils.setAlphaComponent(forName(name), TILE_ALPHA)

    @ColorInt
    fun tint(@ColorInt color: Int): Int = ColorUtils.setAlphaComponent(color, TILE_ALPHA)
}
