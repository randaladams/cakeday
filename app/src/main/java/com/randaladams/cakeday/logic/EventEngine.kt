package com.randaladams.cakeday.logic

import com.randaladams.cakeday.model.FamilyTree
import com.randaladams.cakeday.model.GedDate
import java.time.LocalDate
import java.time.Year

enum class EventKind(val displayName: String, val emoji: String) {
    BIRTHDAY("Birthday", "🎂"),
    ANNIVERSARY("Anniversary", "💍"),
    DEATH("Death", "🕯️"),
    BURIAL("Burial", "🪦"),
    OTHER("Life event", "📜"),
    CUSTOM("My event", "⭐")
}

/** Something that recurs every year on the same month/day. */
data class AnnualEvent(
    val kind: EventKind,
    val personIds: List<String>,
    val familyId: String?,
    val label: String,         // "Birth", "Marriage", "Graduation", ...
    val date: GedDate,
    val place: String?
)

/** An [AnnualEvent] landing on a particular calendar day. */
data class Occurrence(
    val event: AnnualEvent,
    val on: LocalDate,
    /** Years since the original event, if its year is known. */
    val years: Int?,
    /** True if this belongs in the "living" panel. */
    val living: Boolean,
    val names: String
) {
    val headline: String
        get() {
            val y = years
            val yr = event.date.year
            val approx = if (event.date.isApproximate) " (approx. date)" else ""
            return when (event.kind) {
                EventKind.BIRTHDAY -> if (living) {
                    if (y != null) "Birthday — turns $y$approx" else "Birthday$approx"
                } else {
                    if (y != null) "Born $y years ago ($yr)$approx" else "Birth anniversary$approx"
                }
                EventKind.ANNIVERSARY -> if (living) {
                    if (y != null) "Wedding anniversary — ${y} ${plural(y, "year")}$approx" else "Wedding anniversary$approx"
                } else {
                    if (y != null) "Married $y years ago ($yr)$approx" else "Wedding anniversary$approx"
                }
                EventKind.DEATH -> if (y != null) "Died $y ${plural(y, "year")} ago ($yr)$approx" else "Death anniversary$approx"
                EventKind.BURIAL -> if (y != null) "${event.label} $y ${plural(y, "year")} ago ($yr)$approx" else "${event.label} anniversary$approx"
                EventKind.OTHER, EventKind.CUSTOM ->
                    if (y != null && y > 0) "${event.label} — $y ${plural(y, "year")} ago ($yr)$approx"
                    else "${event.label}$approx"
            }
        }

    /** Round-number years: 1, 5, 10, 15, 20, 25 ... */
    val isMilestone: Boolean get() = years != null && years > 0 && (years == 1 || years % 5 == 0)

    private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"
}

class EventEngine(
    private val tree: FamilyTree,
    private val living: LivingCalculator,
    includeApproximate: Boolean = true
) {
    val allEvents: List<AnnualEvent>
    private val byMonthDay: Map<Int, List<AnnualEvent>>

    init {
        val list = mutableListOf<AnnualEvent>()
        for (p in tree.people.values) {
            // Many exports (Ancestry especially) repeat an event once per source.
            // Birth, death, christening and burial happen once, so only the first usable
            // record counts (GEDCOM lists the preferred one first). Other events are
            // merged when the label and date match.
            val seen = HashSet<String>()
            for (e in p.events) {
                val d = e.date ?: continue
                if (!d.hasMonthDay) continue
                if (!includeApproximate && d.isApproximate) continue
                val dedupeKey = if (e.tag in ONCE_PER_LIFE) e.tag
                    else "${e.tag}|${e.label.lowercase()}|${d.year}-${d.month}-${d.day}"
                if (!seen.add(dedupeKey)) continue
                val kind = when (e.tag) {
                    "BIRT" -> EventKind.BIRTHDAY
                    "DEAT" -> EventKind.DEATH
                    "BURI", "CREM" -> EventKind.BURIAL
                    "CUSTOM" -> EventKind.CUSTOM
                    else -> EventKind.OTHER
                }
                list += AnnualEvent(kind, listOf(p.id), null, e.label, d, e.place)
            }
        }
        val seenCouples = HashSet<String>()
        for (f in tree.families.values) {
            if (f.isDivorced) continue
            val m = f.marriage ?: continue
            val d = m.date ?: continue
            if (!d.hasMonthDay) continue
            if (!includeApproximate && d.isApproximate) continue
            if (f.spouseIds.isEmpty()) continue
            // the same couple entered twice as separate families
            if (!seenCouples.add(f.spouseIds.sorted().joinToString() + "|${d.year}-${d.month}-${d.day}")) continue
            list += AnnualEvent(EventKind.ANNIVERSARY, f.spouseIds, f.id, m.label, d, m.place)
        }
        allEvents = list
        byMonthDay = list.groupBy { key(it.date.month!!, it.date.day!!) }
    }

    fun on(date: LocalDate): List<Occurrence> {
        val events = byMonthDay[key(date.monthValue, date.dayOfMonth)].orEmpty().toMutableList()
        // Feb 29 events are shown on Feb 28 in non-leap years
        if (date.monthValue == 2 && date.dayOfMonth == 28 && !Year.isLeap(date.year.toLong())) {
            events += byMonthDay[key(2, 29)].orEmpty()
        }
        return events.mapNotNull { e ->
            val y = e.date.year?.let { date.year - it }
            if (y != null && y < 0) return@mapNotNull null
            Occurrence(e, date, y, isLivingEvent(e), names(e))
        }.sortedWith(compareBy<Occurrence> { it.event.kind.ordinal }.thenBy { it.names })
    }

    fun between(start: LocalDate, endInclusive: LocalDate): List<Occurrence> {
        val out = mutableListOf<Occurrence>()
        var d = start
        while (!d.isAfter(endInclusive)) { out += on(d); d = d.plusDays(1) }
        return out
    }

    /** Next date (today or later) on which this event recurs. */
    fun nextOccurrence(e: AnnualEvent, from: LocalDate): LocalDate {
        var year = from.year
        while (true) {
            val d = dateIn(e.date.month!!, e.date.day!!, year)
            if (d != null && !d.isBefore(from)) return d
            year++
        }
    }

    private fun isLivingEvent(e: AnnualEvent): Boolean = when (e.kind) {
        EventKind.DEATH, EventKind.BURIAL -> false
        EventKind.ANNIVERSARY -> e.personIds.all { living.isLiving(it) }
        else -> living.isLiving(e.personIds.first())
    }

    private fun names(e: AnnualEvent): String =
        e.personIds.mapNotNull { tree.person(it)?.fullName }.joinToString(" & ")

    companion object {
        private val ONCE_PER_LIFE = setOf("BIRT", "DEAT", "CHR", "BAPM", "BURI", "CREM")
        private fun key(month: Int, day: Int) = month * 100 + day

        fun dateIn(month: Int, day: Int, year: Int): LocalDate? = try {
            if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
            else LocalDate.of(year, month, day)
        } catch (_: Exception) { null }
    }
}
