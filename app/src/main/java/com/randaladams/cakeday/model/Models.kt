package com.randaladams.cakeday.model

enum class Sex { M, F, U }

/** A dated life event on a person or a family (BIRT, DEAT, MARR, custom, ...). */
data class LifeEvent(
    val tag: String,          // GEDCOM tag, or "CUSTOM" for user-added events
    val label: String,        // human readable ("Birth", "Graduation", ...)
    val date: GedDate?,
    val place: String? = null,
    val customId: String? = null
)

data class Person(
    val id: String,
    val givenName: String,
    val surname: String,
    val suffix: String = "",
    val sex: Sex = Sex.U,
    val events: List<LifeEvent> = emptyList(),
    val parentFamilyIds: List<String> = emptyList(),
    val spouseFamilyIds: List<String> = emptyList(),
    /** GEDCOM "1 DEAT Y" – known dead with no date. */
    val deathFlag: Boolean = false,
    val notes: String = "",
    val phone: String = "",
    /** null = calculate automatically; true/false = user override. */
    val livingOverride: Boolean? = null,
    val edited: Boolean = false
) {
    val fullName: String
        get() = listOf(givenName, surname, suffix).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "(Unknown)" }

    /** "Smith, John" for sorting. */
    val sortName: String get() = "${surname.lowercase()} ${givenName.lowercase()}"

    fun event(vararg tags: String): LifeEvent? = events.firstOrNull { it.tag in tags }
    val birth: LifeEvent? get() = event("BIRT")
    val death: LifeEvent? get() = event("DEAT")

    val hasDeathEvidence: Boolean
        get() = deathFlag || events.any { it.tag in DEATH_TAGS }

    /** "1950 – 2012", "b. 1950", "" */
    fun lifespan(): String {
        val b = (birth?.date ?: event("CHR", "BAPM")?.date)?.year
        val d = (death?.date ?: event("BURI", "CREM")?.date)?.year
        return when {
            b != null && d != null -> "$b – $d"
            b != null -> "b. $b"
            d != null -> "d. $d"
            else -> ""
        }
    }

    companion object {
        val DEATH_TAGS = setOf("DEAT", "BURI", "CREM", "PROB")
    }
}

data class Family(
    val id: String,
    val husbandId: String?,
    val wifeId: String?,
    val childIds: List<String> = emptyList(),
    val events: List<LifeEvent> = emptyList()
) {
    val marriage: LifeEvent? get() = events.firstOrNull { it.tag == "MARR" }
    val isDivorced: Boolean get() = events.any { it.tag == "DIV" || it.tag == "ANUL" }
    val spouseIds: List<String> get() = listOfNotNull(husbandId, wifeId)
    fun otherSpouse(personId: String): String? = when (personId) {
        husbandId -> wifeId
        wifeId -> husbandId
        else -> null
    }
}

class FamilyTree(
    val people: Map<String, Person>,
    val families: Map<String, Family>
) {
    fun person(id: String?): Person? = id?.let { people[it] }

    fun parentsOf(id: String): List<String> =
        people[id]?.parentFamilyIds.orEmpty().flatMap { families[it]?.spouseIds.orEmpty() }.distinct()

    fun childrenOf(id: String): List<String> =
        people[id]?.spouseFamilyIds.orEmpty().flatMap { families[it]?.childIds.orEmpty() }.distinct()

    fun spousesOf(id: String): List<String> =
        people[id]?.spouseFamilyIds.orEmpty().mapNotNull { families[it]?.otherSpouse(id) }.distinct()

    fun siblingsOf(id: String): List<String> =
        people[id]?.parentFamilyIds.orEmpty().flatMap { families[it]?.childIds.orEmpty() }
            .filter { it != id }.distinct()

    companion object {
        val EMPTY = FamilyTree(emptyMap(), emptyMap())
    }
}
