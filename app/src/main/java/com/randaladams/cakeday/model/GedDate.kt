package com.randaladams.cakeday.model

/**
 * A (possibly partial) date taken from a GEDCOM DATE value, e.g. "12 MAR 1950",
 * "ABT 1890", "BET 1 JAN 1900 AND 1905", or an ISO date typed by the user ("1950-03-12").
 */
data class GedDate(
    val year: Int?,
    val month: Int?,   // 1..12
    val day: Int?,     // 1..31
    val qualifier: String? = null, // ABT, EST, CAL, BEF, AFT, BET, FROM, INT
    val raw: String
) {
    /** True when the date can produce a yearly anniversary (it has a month and a day). */
    val hasMonthDay: Boolean get() = month != null && day != null

    /** True when the date is not exact (about, before, between, ...). */
    val isApproximate: Boolean get() = qualifier != null

    fun display(): String {
        val parts = mutableListOf<String>()
        qualifier?.let { parts += QUALIFIER_TEXT[it] ?: it.lowercase() }
        day?.let { parts += it.toString() }
        month?.let { parts += MONTH_NAMES[it - 1] }
        year?.let { parts += it.toString() }
        return if (parts.isEmpty()) raw else parts.joinToString(" ")
    }

    /** GEDCOM-style text for saving edits, e.g. "12 MAR 1950". */
    fun toGedcom(): String {
        val parts = mutableListOf<String>()
        qualifier?.let { parts += it }
        day?.let { parts += it.toString() }
        month?.let { parts += MONTHS[it - 1] }
        year?.let { parts += it.toString() }
        return parts.joinToString(" ")
    }

    companion object {
        val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private val QUALIFIER_TEXT = mapOf(
            "ABT" to "about", "EST" to "est.", "CAL" to "calc.", "BEF" to "before",
            "AFT" to "after", "BET" to "between", "FROM" to "from", "INT" to "about"
        )
        private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
        private val ISO_MONTH = Regex("""^(\d{4})-(\d{1,2})$""")
        private val FULL_MONTHS = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
            "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
        )

        fun parse(input: String?): GedDate? {
            if (input.isNullOrBlank()) return null
            val raw = input.trim()
            var s = raw.uppercase()
                .replace(Regex("""@#D[A-Z ]+@"""), " ")   // calendar escapes (@#DJULIAN@ etc.)
                .replace(Regex("""\(.*?\)"""), " ")        // INT date phrase text
                .replace(',', ' ')
                .trim()

            ISO.matchEntire(s)?.let { m ->
                val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
                if (mo in 1..12 && d in 1..31) return GedDate(y, mo, d, null, raw)
            }
            ISO_MONTH.matchEntire(s)?.let { m ->
                val mo = m.groupValues[2].toInt()
                if (mo in 1..12) return GedDate(m.groupValues[1].toInt(), mo, null, null, raw)
            }

            var qualifier: String? = null
            val tokens0 = s.split(Regex("""\s+""")).filter { it.isNotEmpty() }.toMutableList()
            if (tokens0.isEmpty()) return null
            when (tokens0[0]) {
                "ABT", "ABOUT", "CIRCA", "C.", "CA", "CA." -> { qualifier = "ABT"; tokens0.removeAt(0) }
                "EST", "CAL", "BEF", "AFT", "INT" -> { qualifier = tokens0[0]; tokens0.removeAt(0) }
                "BEFORE" -> { qualifier = "BEF"; tokens0.removeAt(0) }
                "AFTER" -> { qualifier = "AFT"; tokens0.removeAt(0) }
                "BET", "FROM" -> {
                    qualifier = tokens0[0]; tokens0.removeAt(0)
                    // keep only the first date of a range
                    val stop = tokens0.indexOfFirst { it == "AND" || it == "TO" }
                    if (stop >= 0) while (tokens0.size > stop) tokens0.removeAt(stop)
                }
                "TO" -> { qualifier = "BEF"; tokens0.removeAt(0) }
            }
            s = tokens0.joinToString(" ")

            var year: Int? = null
            var month: Int? = null
            var day: Int? = null
            for (t in tokens0) {
                val monthIdx = MONTHS.indexOf(t).takeIf { it >= 0 }
                    ?: FULL_MONTHS.indexOf(t).takeIf { it >= 0 }
                when {
                    monthIdx != null && month == null -> month = monthIdx + 1
                    t.matches(Regex("""\d{3,4}(/\d{1,4})?""")) && year == null ->
                        year = t.substringBefore('/').toInt()
                    t.matches(Regex("""\d{1,2}""")) && day == null && year == null -> day = t.toInt()
                    t == "BC" || t == "B.C." -> year = year?.let { -it }
                }
            }
            if (day != null && (month == null || day !in 1..31)) day = null
            if (year == null && month == null) return if (s.isEmpty()) null else GedDate(null, null, null, qualifier, raw)
            return GedDate(year, month, day, qualifier, raw)
        }
    }
}
