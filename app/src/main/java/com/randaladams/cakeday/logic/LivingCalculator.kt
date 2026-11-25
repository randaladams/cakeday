package com.randaladams.cakeday.logic

import com.randaladams.cakeday.model.FamilyTree
import com.randaladams.cakeday.model.Person

/**
 * Decides who is living. A person is deceased if the user says so, or there is any
 * death / burial evidence. Otherwise they are living if their (estimated) birth year is
 * within [maxAge] years of today. Birth years are estimated from relatives when missing.
 */
class LivingCalculator(
    private val tree: FamilyTree,
    private val currentYear: Int,
    private val maxAge: Int = 105,
    private val unknownIsLiving: Boolean = false
) {
    data class Status(val living: Boolean, val calculated: Boolean, val estimatedBirthYear: Int?)

    private val cache = HashMap<String, Status>()

    fun isLiving(id: String): Boolean = status(id).living

    fun status(id: String): Status = cache.getOrPut(id) { compute(id) }

    private fun compute(id: String): Status {
        val p = tree.people[id] ?: return Status(false, true, null)
        p.livingOverride?.let { return Status(it, false, birthYear(p)) }
        if (p.hasDeathEvidence) return Status(false, false, birthYear(p))
        val est = birthYear(p) ?: estimateFromRelatives(p)
        val living = if (est == null) unknownIsLiving else currentYear - est <= maxAge
        return Status(living, true, est)
    }

    private fun birthYear(p: Person): Int? =
        (p.birth?.date ?: p.event("CHR", "BAPM")?.date)?.year

    private fun estimateFromRelatives(p: Person): Int? {
        val guesses = mutableListOf<Int>()
        tree.parentsOf(p.id).mapNotNull { tree.person(it)?.let(::birthYear) }.forEach { guesses += it + 25 }
        tree.childrenOf(p.id).mapNotNull { tree.person(it)?.let(::birthYear) }.forEach { guesses += it - 25 }
        tree.spousesOf(p.id).mapNotNull { tree.person(it)?.let(::birthYear) }.forEach { guesses += it }
        tree.siblingsOf(p.id).mapNotNull { tree.person(it)?.let(::birthYear) }.forEach { guesses += it }
        p.spouseFamilyIds.mapNotNull { tree.families[it]?.marriage?.date?.year }.forEach { guesses += it - 25 }
        // any other dated event (graduation, emigration...) – assume an adult
        p.events.mapNotNull { it.date?.year }.minOrNull()?.let { guesses += it - 20 }
        // grandchildren
        if (guesses.isEmpty()) tree.childrenOf(p.id).flatMap { tree.childrenOf(it) }
            .mapNotNull { tree.person(it)?.let(::birthYear) }.forEach { guesses += it - 50 }
        // Use the earliest guess: errs toward "older", so long-gone ancestors aren't shown as living
        return guesses.minOrNull()
    }
}
