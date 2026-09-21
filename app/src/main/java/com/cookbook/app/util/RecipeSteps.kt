package com.cookbook.app.util

/**
 * Zerlegt den Zubereitungstext in einzelne Schritte.
 *
 * `instructions` ist im Datenmodell ein Textblock. Getrennt wird an Leerzeilen
 * oder an führender Nummerierung ("1.", "2)", "Schritt 3:"). Schrittname und
 * Teilzeit sind optional und entfallen, wenn der Text sie nicht hergibt.
 */
object RecipeSteps {

    /** "Schritt 2:", "2.", "2)" am Zeilenanfang. */
    private val NUMBERED_LINE = Regex("""^\s*(?:Schritt\s*)?(\d{1,2})\s*[.):]\s*""", RegexOption.IGNORE_CASE)

    /** Kurze Kopfzeile mit Doppelpunkt, z. B. "Zwiebeln anschwitzen: ..." */
    private val NAMED_HEAD = Regex("""^([^.:\n]{3,40}):\s+(.*)$""", RegexOption.DOT_MATCHES_ALL)

    /** Teilzeit im Schritttext, z. B. "12 Minuten", "ca. 5 Min." */
    private val DURATION = Regex("""(\d{1,3})\s*(?:Minuten|Minute|Min\.?|min)\b""")

    data class Step(
        val index: Int,
        val name: String?,
        val text: String,
        val minutes: Int?
    ) {
        /** Zweistellige Ziffer für die Schrittmarke ("01"). */
        val label: String get() = (index + 1).toString().padStart(2, '0')
    }

    fun parse(instructions: String): List<Step> {
        val raw = splitRaw(instructions)
        return raw.mapIndexed { index, body ->
            val head = NAMED_HEAD.matchEntire(body)
            val name = head?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && !it.contains('\n') }
            val text = if (name != null) head.groupValues[2].trim() else body
            Step(
                index = index,
                name = name,
                text = text,
                minutes = DURATION.find(body)?.groupValues?.get(1)?.toIntOrNull()
            )
        }
    }

    private fun splitRaw(instructions: String): List<String> {
        val normalized = instructions.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        // Führende Nummerierung hat Vorrang — sie trennt auch ohne Leerzeile.
        val lines = normalized.split("\n")
        val numberedCount = lines.count { NUMBERED_LINE.containsMatchIn(it) }
        if (numberedCount >= 2) {
            val steps = mutableListOf<StringBuilder>()
            for (line in lines) {
                if (NUMBERED_LINE.containsMatchIn(line)) {
                    steps.add(StringBuilder(NUMBERED_LINE.replace(line, "").trim()))
                } else if (steps.isNotEmpty() && line.isNotBlank()) {
                    steps.last().append(' ').append(line.trim())
                }
            }
            return steps.map { it.toString().trim() }.filter { it.isNotEmpty() }
        }

        val byBlankLine = normalized.split(Regex("\n\\s*\n"))
            .map { it.replace("\n", " ").trim() }
            .filter { it.isNotEmpty() }
        if (byBlankLine.size > 1) return byBlankLine

        val byLine = lines.map { it.trim() }.filter { it.isNotEmpty() }
        return if (byLine.size > 1) byLine else listOf(normalized)
    }

    /**
     * Ordnet einem Schritt die Zutaten zu, die in seinem Text vorkommen.
     * Gematcht wird das erste Wort des Zutatennamens, ohne Groß-/Kleinschreibung.
     */
    fun <T> ingredientsFor(step: Step, ingredients: List<T>, nameOf: (T) -> String): List<T> {
        val haystack = ((step.name ?: "") + " " + step.text).lowercase()
        return ingredients.filter { ingredient ->
            val firstWord = nameOf(ingredient).trim().substringBefore(' ').lowercase()
            firstWord.length >= 3 && haystack.contains(firstWord)
        }
    }
}
