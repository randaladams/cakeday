package com.randaladams.cakeday.data

import com.randaladams.cakeday.model.CustomEvent
import com.randaladams.cakeday.model.Edits
import com.randaladams.cakeday.model.FamilyEdit
import com.randaladams.cakeday.model.PersonEdit
import com.randaladams.cakeday.model.Sex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves the user's edits as JSON in the app's private storage (never in the .ged file). */
class EditStore(private val file: File) {

    fun load(): Edits {
        if (!file.exists()) return Edits()
        return try {
            val root = JSONObject(file.readText())
            val persons = root.optJSONObject("persons")?.let { o ->
                o.keys().asSequence().associateWith { k -> personFrom(o.getJSONObject(k)) }
            } ?: emptyMap()
            val families = root.optJSONObject("families")?.let { o ->
                o.keys().asSequence().associateWith { k ->
                    val f = o.getJSONObject(k)
                    FamilyEdit(f.str("marriageDate"), f.bool("divorced"))
                }
            } ?: emptyMap()
            val customs = root.optJSONArray("customEvents")?.let { a ->
                (0 until a.length()).map { i ->
                    val c = a.getJSONObject(i)
                    CustomEvent(c.getString("id"), c.getString("personId"), c.optString("label"), c.optString("date"))
                }
            } ?: emptyList()
            Edits(persons, families, customs)
        } catch (e: Exception) {
            Edits()
        }
    }

    fun save(edits: Edits) {
        val root = JSONObject()
        root.put("persons", JSONObject().apply {
            edits.persons.filterValues { !it.isEmpty }.forEach { (id, p) -> put(id, personTo(p)) }
        })
        root.put("families", JSONObject().apply {
            edits.families.forEach { (id, f) ->
                put(id, JSONObject().apply {
                    f.marriageDate?.let { put("marriageDate", it) }
                    f.divorced?.let { put("divorced", it) }
                })
            }
        })
        root.put("customEvents", JSONArray().apply {
            edits.customEvents.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("personId", c.personId); put("label", c.label); put("date", c.date)
                })
            }
        })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(2))
        tmp.renameTo(file)
    }

    private fun personFrom(o: JSONObject) = PersonEdit(
        givenName = o.str("givenName"),
        surname = o.str("surname"),
        sex = o.str("sex")?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
        birthDate = o.str("birthDate"),
        birthPlace = o.str("birthPlace"),
        deathDate = o.str("deathDate"),
        deathPlace = o.str("deathPlace"),
        livingOverride = o.bool("livingOverride"),
        phone = o.str("phone"),
        notes = o.str("notes")
    )

    private fun personTo(p: PersonEdit) = JSONObject().apply {
        p.givenName?.let { put("givenName", it) }
        p.surname?.let { put("surname", it) }
        p.sex?.let { put("sex", it.name) }
        p.birthDate?.let { put("birthDate", it) }
        p.birthPlace?.let { put("birthPlace", it) }
        p.deathDate?.let { put("deathDate", it) }
        p.deathPlace?.let { put("deathPlace", it) }
        p.livingOverride?.let { put("livingOverride", it) }
        p.phone?.let { put("phone", it) }
        p.notes?.let { put("notes", it) }
    }

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.bool(k: String): Boolean? = if (has(k) && !isNull(k)) getBoolean(k) else null
}
