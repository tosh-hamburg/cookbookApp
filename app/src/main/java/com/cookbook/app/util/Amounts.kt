package com.cookbook.app.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Mengen und Zahlen im Format des Design-Systems.
 *
 * Regeln: Dezimalkomma, höchstens zwei Nachkommastellen, leere Werte nie als "0".
 */
object Amounts {

    /** Führende Zahl (auch "1/2" oder "1 1/2") plus Rest als Einheit. */
    private val FRACTION = Regex("""^\s*(\d+)?\s*(\d+)\s*/\s*(\d+)\s*(.*)$""")
    private val DECIMAL = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(.*)$""")

    /**
     * Rechnet einen Mengen-String auf die gewählte Portionszahl um.
     *
     * Nicht parsebare Angaben ("etwas", "nach Geschmack") bleiben unverändert.
     */
    fun scale(amount: String, factor: Double): String {
        if (amount.isBlank()) return amount
        if (abs(factor - 1.0) < 0.0001) return amount.trim()

        FRACTION.matchEntire(amount)?.let { match ->
            val whole = match.groupValues[1].toIntOrNull() ?: 0
            val numerator = match.groupValues[2].toIntOrNull() ?: return@let
            val denominator = match.groupValues[3].toIntOrNull()?.takeIf { it != 0 } ?: return@let
            val value = (whole + numerator.toDouble() / denominator) * factor
            return join(format(value), match.groupValues[4])
        }

        DECIMAL.matchEntire(amount)?.let { match ->
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
            return join(format(value * factor), match.groupValues[2])
        }

        return amount.trim()
    }

    /** Zahl mit Dezimalkomma, höchstens zwei Nachkommastellen, ohne Nullen am Ende. */
    fun format(value: Double): String {
        if (abs(value - value.roundToInt()) < 0.005) return value.roundToInt().toString()
        return String.format(Locale.GERMANY, "%.2f", value)
            .trimEnd('0')
            .trimEnd(',')
    }

    private fun join(number: String, unit: String): String {
        val trimmed = unit.trim()
        return if (trimmed.isEmpty()) number else "$number $trimmed"
    }

    /** Skalierungsfaktor, abgesichert gegen 0 Portionen im Rezept. */
    fun factor(targetServings: Int, recipeServings: Int): Double {
        val base = recipeServings.takeIf { it > 0 } ?: 1
        return targetServings.toDouble() / base
    }
}
