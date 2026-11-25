package com.randaladams.cakeday

import com.randaladams.cakeday.gedcom.GedcomParser
import com.randaladams.cakeday.logic.EventEngine
import com.randaladams.cakeday.logic.EventKind
import com.randaladams.cakeday.logic.LivingCalculator
import com.randaladams.cakeday.logic.NotificationFilter
import com.randaladams.cakeday.logic.RelationshipCalculator
import com.randaladams.cakeday.model.AppSettings
import com.randaladams.cakeday.model.CustomEvent
import com.randaladams.cakeday.model.Edits
import com.randaladams.cakeday.model.GedDate
import com.randaladams.cakeday.model.PeopleScope
import com.randaladams.cakeday.model.PersonEdit
import com.randaladams.cakeday.model.RelativeScope
import com.randaladams.cakeday.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val tree = GedcomParser.parse(javaClass.getResource("/sample.ged")!!.readText())
    private val living = LivingCalculator(tree, 2026)
    private val rel = RelationshipCalculator(tree, "@I1@")
    private fun label(id: String) = rel.relationship(id)?.label

    @Test fun parsesPeopleAndFamilies() {
        assertEquals(11, tree.people.size)
        assertEquals(6, tree.families.size)
        val george = tree.people.getValue("@I2@")
        assertEquals("George", george.givenName)
        assertEquals("Adams", george.surname)
        assertEquals("Sr.", george.suffix)
        assertEquals("Dallas, Texas", george.death?.place)
    }

    @Test fun parsesDates() {
        assertEquals(GedDate(1950, 3, 12, null, "12 MAR 1950"), GedDate.parse("12 MAR 1950"))
        assertEquals(1890, GedDate.parse("ABT 1890")?.year)
        assertEquals("ABT", GedDate.parse("ABT 1890")?.qualifier)
        assertEquals(3, GedDate.parse("BET 3 MAR 1900 AND 1905")?.day)
        assertEquals(1731, GedDate.parse("@#DJULIAN@ 11 FEB 1731/32")?.year)
        assertEquals(12, GedDate.parse("1950-03-12")?.day)
        assertFalse(GedDate.parse("MAR 1950")!!.hasMonthDay)
    }

    @Test fun relationships() {
        assertEquals("Home person (you)", label("@I1@"))
        assertEquals("Father", label("@I2@"))
        assertEquals("Mother", label("@I3@"))
        assertEquals("Sister", label("@I4@"))
        assertEquals("Grandfather", label("@I5@"))
        assertEquals("Uncle", label("@I6@"))
        assertEquals("1st cousin", label("@I7@"))
        assertEquals("1st cousin once removed", label("@I8@"))
        assertEquals("Wife", label("@I9@"))
        assertEquals("Brother-in-law", label("@I10@"))
        assertEquals("Aunt (by marriage)", label("@I11@"))
    }

    @Test fun relationshipNames() {
        assertEquals("Great-great-grandfather", RelationshipCalculator.bloodLabel(4, 0, Sex.M, false))
        assertEquals("3rd great-grandmother", RelationshipCalculator.bloodLabel(5, 0, Sex.F, false))
        assertEquals("Great-great-aunt", RelationshipCalculator.bloodLabel(4, 1, Sex.F, false))
        assertEquals("2nd cousin twice removed", RelationshipCalculator.bloodLabel(3, 5, Sex.U, false))
        assertEquals("Grandniece", RelationshipCalculator.bloodLabel(1, 3, Sex.F, false))
    }

    @Test fun livingStatus() {
        assertFalse(living.isLiving("@I2@"))   // has death date
        assertTrue(living.isLiving("@I3@"))    // born 1944
        assertFalse(living.isLiving("@I5@"))   // ABT 1910 -> 116 years
        assertTrue(living.isLiving("@I11@"))   // estimated from child born 1975
    }

    @Test fun dailyEventsSplitIntoPanels() {
        val engine = EventEngine(tree, living)
        val day = engine.on(LocalDate.of(2026, 11, 25))
        val livingNames = day.filter { it.living }.map { it.names }
        val deceased = day.filterNot { it.living }
        assertTrue("Randal Adams" in livingNames)
        assertTrue("Randal Adams & Karen Smith" in livingNames)
        assertEquals(1, deceased.size)
        assertEquals(EventKind.DEATH, deceased[0].event.kind)
        assertEquals(16, deceased[0].years)
    }

    @Test fun leapDayShowsOnFeb28() {
        val engine = EventEngine(tree, living)
        assertTrue(engine.on(LocalDate.of(2027, 2, 28)).any { it.names == "Mary Jones" })
        assertTrue(engine.on(LocalDate.of(2028, 2, 29)).any { it.names == "Mary Jones" })
        assertFalse(engine.on(LocalDate.of(2028, 2, 28)).any { it.names == "Mary Jones" })
    }

    @Test fun editsLayerOnTopWithoutTouchingOriginal() {
        val edits = Edits(
            persons = mapOf("@I9@" to PersonEdit(givenName = "Kathy", deathDate = "1 JAN 2020")),
            customEvents = listOf(CustomEvent("c1", "@I1@", "Moved to Texas", "2001-11-25"))
        )
        val edited = edits.applyTo(tree)
        assertEquals("Kathy", edited.people.getValue("@I9@").givenName)
        assertEquals("Karen", tree.people.getValue("@I9@").givenName)
        assertFalse(LivingCalculator(edited, 2026).isLiving("@I9@"))
        val events = EventEngine(edited, LivingCalculator(edited, 2026)).on(LocalDate.of(2026, 11, 25))
        assertTrue(events.any { it.event.kind == EventKind.CUSTOM && it.years == 25 })
    }

    @Test fun notificationFilters() {
        val engine = EventEngine(tree, living)
        val day = engine.on(LocalDate.of(2026, 11, 25))
        val livingOnly = NotificationFilter.filter(day, AppSettings(notifyScope = PeopleScope.LIVING), rel)
        assertTrue(livingOnly.all { it.living })
        val birthdaysOnly = NotificationFilter.filter(
            day, AppSettings(notifyAnniversaries = false, notifyDeaths = false, notifyCustomEvents = false), rel
        )
        assertTrue(birthdaysOnly.all { it.event.kind == EventKind.BIRTHDAY })
        val close = NotificationFilter.filter(day, AppSettings(notifyRelatives = RelativeScope.CLOSE), rel)
        assertFalse(close.any { it.names == "Lucy Adams" }) // 1st cousin once removed
        assertNull(rel.relationship("@NOPE@"))
    }

    @Test fun duplicateSourceRecordsShowOnce() {
        val ged = """
            0 @I1@ INDI
            1 NAME Kevin Shelby /Adams/
            1 BIRT
            2 DATE 28 OCT 1971
            2 PLAC Memphis, Shelby, Tennessee, USA
            1 BIRT
            2 DATE 28 OCT 1971
            2 PLAC MEMPHIS TENNESSEE
            1 DEAT
            2 DATE 11 APR 1994
            2 PLAC Bartlett, Shelby County, Tennessee, USA
            1 DEAT
            2 DATE 11 APR 1994
            2 PLAC MEMPHIS TENNESSEE
        """.trimIndent()
        val t = GedcomParser.parse(ged)
        val engine = EventEngine(t, LivingCalculator(t, 2026))
        assertEquals(2, engine.allEvents.size)
        assertEquals(1, engine.on(LocalDate.of(2026, 10, 28)).size)
        assertEquals(1, engine.on(LocalDate.of(2026, 4, 11)).size)
    }
}
