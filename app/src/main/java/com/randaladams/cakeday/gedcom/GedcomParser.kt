package com.randaladams.cakeday.gedcom

import com.randaladams.cakeday.model.Family
import com.randaladams.cakeday.model.FamilyTree
import com.randaladams.cakeday.model.GedDate
import com.randaladams.cakeday.model.LifeEvent
import com.randaladams.cakeday.model.Person
import com.randaladams.cakeday.model.Sex
import java.io.InputStream
import java.nio.charset.Charset

/**
 * A forgiving GEDCOM 5.5 / 5.5.1 / 7 reader. It only reads – the source file is never written.
 * Pulls out individuals, families, names, sex and dated events.
 */
object GedcomParser {

    val PERSON_EVENT_LABELS = linkedMapOf(
        "BIRT" to "Birth", "CHR" to "Christening", "BAPM" to "Baptism", "DEAT" to "Death",
        "BURI" to "Burial", "CREM" to "Cremation", "ADOP" to "Adoption", "BARM" to "Bar Mitzvah",
        "BASM" to "Bat Mitzvah", "BLES" to "Blessing", "CHRA" to "Adult christening",
        "CONF" to "Confirmation", "FCOM" to "First communion", "ORDN" to "Ordination",
        "NATU" to "Naturalization", "EMIG" to "Emigration", "IMMI" to "Immigration",
        "GRAD" to "Graduation", "RETI" to "Retirement", "PROB" to "Probate", "EVEN" to "Event"
    )
    val FAMILY_EVENT_LABELS = linkedMapOf(
        "MARR" to "Marriage", "ENGA" to "Engagement", "MARB" to "Marriage banns",
        "DIV" to "Divorce", "ANUL" to "Annulment", "EVEN" to "Event"
    )

    private val LINE = Regex("""^\s*(\d+)\s+(?:(@[^@]+@)\s+)?(\S+)(?:\s(.*))?$""")

    fun parse(input: InputStream): FamilyTree = parse(decode(input.readBytes()))

    /** Detects the character set (UTF-8 / UTF-16 / ANSI) and returns text. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val head = String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1).uppercase()
        val charset: Charset = when {
            Regex("""1\s+CHAR\s+(ANSI|WINDOWS|IBM)""").containsMatchIn(head) -> Charset.forName("windows-1252")
            else -> Charsets.UTF_8
        }
        return String(bytes, start, bytes.size - start, charset)
    }

    fun parse(text: String): FamilyTree {
        val people = LinkedHashMap<String, PersonBuilder>()
        val families = LinkedHashMap<String, FamilyBuilder>()

        var person: PersonBuilder? = null
        var family: FamilyBuilder? = null
        var event: EventBuilder? = null      // current level-1 event
        var inName = false                   // inside the first level-1 NAME
        var noteTarget: StringBuilder? = null
        var noteLevel = -1

        fun finishEvent() {
            event?.let { e ->
                val ev = e.build()
                person?.events?.add(ev) ?: family?.events?.add(ev)
            }
            event = null
        }

        for (rawLine in text.lineSequence()) {
            val m = LINE.matchEntire(rawLine.trimEnd('\r')) ?: continue
            val level = m.groupValues[1].toInt()
            val xref = m.groupValues[2].ifEmpty { null }
            val tag = m.groupValues[3].uppercase()
            val value = m.groupValues[4]

            // note continuation lines
            if (noteTarget != null && level == noteLevel + 1 && (tag == "CONC" || tag == "CONT")) {
                if (tag == "CONT") noteTarget.append('\n')
                noteTarget.append(value)
                continue
            }
            if (noteTarget != null && level <= noteLevel) { noteTarget = null; noteLevel = -1 }

            when (level) {
                0 -> {
                    finishEvent()
                    person = null; family = null; inName = false
                    when (value.trim().uppercase()) {
                        "INDI" -> if (xref != null) person = PersonBuilder(xref).also { people[xref] = it }
                        "FAM" -> if (xref != null) family = FamilyBuilder(xref).also { families[xref] = it }
                    }
                    // GEDCOM 7 puts the record type in the tag position: "0 @I1@ INDI"
                    if (person == null && family == null && xref != null) {
                        if (tag == "INDI") person = PersonBuilder(xref).also { people[xref] = it }
                        if (tag == "FAM") family = FamilyBuilder(xref).also { families[xref] = it }
                    }
                }
                1 -> {
                    finishEvent(); inName = false
                    person?.let { p ->
                        when (tag) {
                            "NAME" -> if (!p.hasName) { p.setName(value); inName = true }
                            "SEX" -> p.sex = when (value.trim().uppercase().firstOrNull()) {
                                'M' -> Sex.M; 'F' -> Sex.F; else -> Sex.U
                            }
                            "FAMC" -> p.famc += value.trim()
                            "FAMS" -> p.fams += value.trim()
                            "NOTE" -> if (!value.trim().startsWith("@")) {
                                if (p.notes.isNotEmpty()) p.notes.append("\n\n")
                                p.notes.append(value); noteTarget = p.notes; noteLevel = 1
                            }
                            else -> if (tag in PERSON_EVENT_LABELS) {
                                event = EventBuilder(tag, PERSON_EVENT_LABELS.getValue(tag))
                                if (tag == "DEAT" && value.trim().uppercase() == "Y") p.deathFlag = true
                            }
                        }
                    }
                    family?.let { f ->
                        when (tag) {
                            "HUSB" -> f.husb = value.trim()
                            "WIFE" -> f.wife = value.trim()
                            "CHIL" -> f.children += value.trim()
                            else -> if (tag in FAMILY_EVENT_LABELS) event = EventBuilder(tag, FAMILY_EVENT_LABELS.getValue(tag))
                        }
                    }
                }
                2 -> {
                    if (inName) person?.let { p ->
                        when (tag) {
                            "GIVN" -> p.given = value.trim()
                            "SURN" -> p.surname = value.trim()
                            "NSFX" -> p.suffix = value.trim()
                        }
                    }
                    event?.let { e ->
                        when (tag) {
                            "DATE" -> e.date = value
                            "PLAC" -> e.place = value
                            "TYPE" -> if (e.tag == "EVEN" && value.isNotBlank()) e.label = value.trim()
                        }
                    }
                }
            }
        }
        finishEvent()

        // Make sure links work in both directions (some exporters omit FAMS/FAMC)
        for (f in families.values) {
            listOfNotNull(f.husb, f.wife).forEach { pid -> people[pid]?.let { if (f.id !in it.fams) it.fams += f.id } }
            f.children.forEach { cid -> people[cid]?.let { if (f.id !in it.famc) it.famc += f.id } }
        }
        for (p in people.values) {
            p.fams.forEach { fid ->
                families[fid]?.let { f ->
                    if (f.husb != p.id && f.wife != p.id) {
                        if (f.husb == null && p.sex != Sex.F) f.husb = p.id
                        else if (f.wife == null) f.wife = p.id
                    }
                }
            }
            p.famc.forEach { fid -> families[fid]?.let { if (p.id !in it.children) it.children += p.id } }
        }

        return FamilyTree(
            people = people.mapValues { it.value.build(families.keys) },
            families = families.mapValues { it.value.build(people.keys) }
        )
    }

    private class EventBuilder(val tag: String, var label: String) {
        var date: String? = null
        var place: String? = null
        fun build() = LifeEvent(tag, label, GedDate.parse(date), place?.trim()?.ifEmpty { null })
    }

    private class PersonBuilder(val id: String) {
        var given = ""
        var surname = ""
        var suffix = ""
        var hasName = false
        var sex = Sex.U
        var deathFlag = false
        val events = mutableListOf<LifeEvent>()
        val famc = mutableListOf<String>()
        val fams = mutableListOf<String>()
        val notes = StringBuilder()

        fun setName(value: String) {
            hasName = true
            val v = value.trim()
            val first = v.indexOf('/')
            if (first >= 0) {
                val second = v.indexOf('/', first + 1)
                given = v.substring(0, first).trim()
                if (second > first) {
                    surname = v.substring(first + 1, second).trim()
                    suffix = v.substring(second + 1).trim()
                } else surname = v.substring(first + 1).trim()
            } else given = v
        }

        fun build(familyIds: Set<String>) = Person(
            id = id, givenName = given, surname = surname, suffix = suffix, sex = sex,
            events = events.toList(),
            parentFamilyIds = famc.filter { it in familyIds }.distinct(),
            spouseFamilyIds = fams.filter { it in familyIds }.distinct(),
            deathFlag = deathFlag, notes = notes.toString().trim()
        )
    }

    private class FamilyBuilder(val id: String) {
        var husb: String? = null
        var wife: String? = null
        val children = mutableListOf<String>()
        val events = mutableListOf<LifeEvent>()
        fun build(personIds: Set<String>) = Family(
            id = id,
            husbandId = husb?.takeIf { it in personIds },
            wifeId = wife?.takeIf { it in personIds },
            childIds = children.filter { it in personIds }.distinct(),
            events = events.toList()
        )
    }
}
