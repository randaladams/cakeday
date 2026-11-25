package com.randaladams.cakeday.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.logic.AnnualEvent
import com.randaladams.cakeday.logic.EventKind
import com.randaladams.cakeday.model.Person
import java.time.LocalDate
import java.time.ZoneId

/** Phone, text and calendar actions. These hand off to the user's own apps – no extra permissions needed. */
object Actions {

    fun dial(ctx: Context, number: String) =
        launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))

    fun text(ctx: Context, number: String, body: String? = null) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
        if (!body.isNullOrBlank()) i.putExtra("sms_body", body)
        launch(ctx, i)
    }

    fun openContact(ctx: Context, contactId: Long, lookupKey: String) {
        val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
        launch(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * Opens Google Calendar (or another calendar app) with a yearly, all-day event filled in.
     * The user confirms which calendar/account to save it to.
     */
    fun addToCalendar(ctx: Context, state: TreeState, e: AnnualEvent) {
        val date = state.engine.nextOccurrence(e, LocalDate.now())
        val names = e.personIds.mapNotNull { state.tree.person(it)?.fullName }.joinToString(" & ")
        val year = e.date.year
        val livingEvent = state.engine.on(date).firstOrNull { it.event == e }?.living ?: false
        val title = when (e.kind) {
            EventKind.BIRTHDAY -> if (livingEvent) "🎂 $names's birthday" else "🎂 $names born${year?.let { " ($it)" } ?: ""}"
            EventKind.ANNIVERSARY -> if (livingEvent) "💍 $names's anniversary" else "💍 $names married${year?.let { " ($it)" } ?: ""}"
            EventKind.DEATH -> "🕯️ Remembering $names${year?.let { " (died $it)" } ?: ""}"
            else -> "${e.kind.emoji} $names: ${e.label}${year?.let { " ($it)" } ?: ""}"
        }
        val rel = e.personIds.mapNotNull { id ->
            state.relationships.relationship(id)?.let { "${state.tree.person(id)?.fullName}: ${it.label}" }
        }
        val desc = buildString {
            append("${e.label}: ${e.date.display()}")
            e.place?.let { append("\n$it") }
            if (rel.isNotEmpty()) append("\n" + rel.joinToString("\n"))
            append("\n\nAdded from Cakeday")
        }
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 24L * 60 * 60 * 1000)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, desc)
            .putExtra(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        // Prefer Google Calendar; fall back to whatever calendar app is installed
        try {
            ctx.startActivity(Intent(intent).setPackage("com.google.android.calendar"))
        } catch (_: ActivityNotFoundException) {
            launch(ctx, intent)
        }
    }

    private fun launch(ctx: Context, i: Intent) {
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app found to handle this", Toast.LENGTH_SHORT).show()
        }
    }
}

data class PhoneNumber(val number: String, val type: String)
data class ContactMatch(val contactId: Long, val lookupKey: String, val name: String, val numbers: List<PhoneNumber>, val strong: Boolean)

object ContactLookup {
    /** Finds phone contacts whose name matches the person. Requires READ_CONTACTS. */
    fun find(ctx: Context, person: Person): List<ContactMatch> {
        val given = person.givenName.split(" ").firstOrNull { it.isNotBlank() }?.lowercase().orEmpty()
        val surname = person.surname.lowercase()
        if (given.isEmpty() && surname.isEmpty()) return emptyList()
        val term = if (surname.isNotEmpty()) surname else given

        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        val byContact = LinkedHashMap<Long, Triple<String, String, MutableList<PhoneNumber>>>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$term%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, c.getInt(4), c.getString(5)).toString()
                val entry = byContact.getOrPut(id) { Triple(c.getString(1), c.getString(2) ?: "", mutableListOf()) }
                val num = c.getString(3) ?: continue
                if (entry.third.none { it.number.filter(Char::isDigit) == num.filter(Char::isDigit) }) entry.third += PhoneNumber(num, type)
            }
        }
        return byContact.map { (id, t) ->
            val n = t.second.lowercase()
            val strong = (given.isEmpty() || n.contains(given)) && (surname.isEmpty() || n.contains(surname))
            ContactMatch(id, t.first, t.second, t.third, strong)
        }.sortedByDescending { it.strong }.take(15)
    }
}
