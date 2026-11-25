package com.randaladams.cakeday.model

/**
 * The user's changes, kept separately from the imported .ged file.
 * The original file is never modified; these are layered on top when the tree is loaded.
 * A null field means "use the value from the .ged file".
 */
data class PersonEdit(
    val givenName: String? = null,
    val surname: String? = null,
    val sex: Sex? = null,
    val birthDate: String? = null,
    val birthPlace: String? = null,
    val deathDate: String? = null,
    val deathPlace: String? = null,
    val livingOverride: Boolean? = null,   // null = automatic
    val phone: String? = null,
    val notes: String? = null
) {
    val isEmpty: Boolean
        get() = this == PersonEdit()
}

data class FamilyEdit(
    val marriageDate: String? = null,
    val divorced: Boolean? = null
)

data class CustomEvent(
    val id: String,
    val personId: String,
    val label: String,
    val date: String
)

data class Edits(
    val persons: Map<String, PersonEdit> = emptyMap(),
    val families: Map<String, FamilyEdit> = emptyMap(),
    val customEvents: List<CustomEvent> = emptyList()
) {
    fun applyTo(tree: FamilyTree): FamilyTree {
        if (persons.isEmpty() && families.isEmpty() && customEvents.isEmpty()) return tree
        val customByPerson = customEvents.groupBy { it.personId }
        val people = tree.people.mapValues { (id, p) ->
            val e = persons[id]
            val customs = customByPerson[id].orEmpty()
            if (e == null && customs.isEmpty()) return@mapValues p
            var events = p.events
            if (e != null) {
                events = replaceEvent(events, "BIRT", "Birth", e.birthDate, e.birthPlace)
                events = replaceEvent(events, "DEAT", "Death", e.deathDate, e.deathPlace)
            }
            events = events + customs.map {
                LifeEvent("CUSTOM", it.label.ifBlank { "Event" }, GedDate.parse(it.date), null, it.id)
            }
            p.copy(
                givenName = e?.givenName ?: p.givenName,
                surname = e?.surname ?: p.surname,
                sex = e?.sex ?: p.sex,
                events = events,
                livingOverride = e?.livingOverride ?: p.livingOverride,
                phone = e?.phone ?: p.phone,
                notes = e?.notes ?: p.notes,
                edited = true
            )
        }
        val fams = tree.families.mapValues { (id, f) ->
            val e = families[id] ?: return@mapValues f
            var events = replaceEvent(f.events, "MARR", "Marriage", e.marriageDate, null)
            when (e.divorced) {
                true -> if (events.none { it.tag == "DIV" }) events = events + LifeEvent("DIV", "Divorce", null)
                false -> events = events.filterNot { it.tag == "DIV" || it.tag == "ANUL" }
                null -> {}
            }
            f.copy(events = events)
        }
        return FamilyTree(people, fams)
    }

    private fun replaceEvent(events: List<LifeEvent>, tag: String, label: String, date: String?, place: String?): List<LifeEvent> {
        if (date == null && place == null) return events
        val old = events.firstOrNull { it.tag == tag }
        val newDate = if (date != null) GedDate.parse(date) else old?.date
        val newPlace = place ?: old?.place
        if (newDate == null && newPlace.isNullOrBlank()) return events.filterNot { it.tag == tag }
        val ev = LifeEvent(tag, old?.label ?: label, newDate, newPlace?.ifBlank { null })
        return if (old == null) events + ev else events.map { if (it === old) ev else it }
    }
}
