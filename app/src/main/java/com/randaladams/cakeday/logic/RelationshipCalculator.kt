package com.randaladams.cakeday.logic

import com.randaladams.cakeday.model.FamilyTree
import com.randaladams.cakeday.model.Sex

data class Relationship(
    val label: String,
    /** Generations from home person up to the common ancestor (blood relatives only). */
    val up: Int = -1,
    /** Generations from the common ancestor down to the other person. */
    val down: Int = -1,
    val kind: Kind
) {
    enum class Kind { SELF, BLOOD, SPOUSE, IN_LAW, STEP, SPOUSE_OF_RELATIVE }

    val isBlood get() = kind == Kind.BLOOD || kind == Kind.SELF

    /** Close family: everyone up to first cousins, plus spouse and immediate in-laws. */
    val isClose: Boolean
        get() = when (kind) {
            Kind.SELF, Kind.SPOUSE, Kind.IN_LAW, Kind.STEP -> true
            Kind.BLOOD -> up + down <= 4 && minOf(up, down) <= 2
            Kind.SPOUSE_OF_RELATIVE -> false
        }
}

/**
 * Works out how anyone in the tree is related to the home person:
 * father, great-grandmother, 2nd cousin once removed, sister-in-law, ...
 */
class RelationshipCalculator(private val tree: FamilyTree, private val homeId: String?) {

    private val homeAncestors: Map<String, Int> = homeId?.let { ancestorsWithDepth(it) } ?: emptyMap()
    private val cache = HashMap<String, Relationship?>()

    fun relationship(targetId: String): Relationship? {
        if (homeId == null || tree.people[homeId] == null) return null
        return cache.getOrPut(targetId) { compute(targetId) }
    }

    private fun compute(targetId: String): Relationship? {
        val home = homeId ?: return null
        if (targetId == home) return Relationship("Home person (you)", 0, 0, Relationship.Kind.SELF)
        val sex = tree.people[targetId]?.sex ?: Sex.U

        blood(home, targetId, homeAncestors)?.let { (up, down) ->
            return Relationship(bloodLabel(up, down, sex, isHalf(home, targetId, up, down)), up, down, Relationship.Kind.BLOOD)
        }

        // Spouse
        if (targetId in tree.spousesOf(home)) {
            val label = when (sex) { Sex.M -> "Husband"; Sex.F -> "Wife"; Sex.U -> "Spouse" }
            return Relationship(label, kind = Relationship.Kind.SPOUSE)
        }

        // Target is a blood relative of home's spouse -> in-law
        for (spouse in tree.spousesOf(home)) {
            val anc = ancestorsWithDepth(spouse)
            blood(spouse, targetId, anc)?.let { (up, down) ->
                val label = when {
                    up == 1 && down == 0 -> gendered(sex, "Father-in-law", "Mother-in-law", "Parent-in-law")
                    up == 1 && down == 1 -> gendered(sex, "Brother-in-law", "Sister-in-law", "Sibling-in-law")
                    up == 0 && down == 1 -> return Relationship(gendered(sex, "Stepson", "Stepdaughter", "Stepchild"), kind = Relationship.Kind.STEP)
                    else -> "Spouse's " + bloodLabel(up, down, sex, false).lowercase()
                }
                val kind = if (up + down <= 2) Relationship.Kind.IN_LAW else Relationship.Kind.SPOUSE_OF_RELATIVE
                return Relationship(label, kind = kind)
            }
        }

        // Target is the spouse of a blood relative of home
        for (spouseOfTarget in tree.spousesOf(targetId)) {
            val (up, down) = blood(home, spouseOfTarget, homeAncestors) ?: continue
            if (spouseOfTarget == home) continue
            return when {
                up == 1 && down == 0 ->
                    Relationship(gendered(sex, "Stepfather", "Stepmother", "Step-parent"), kind = Relationship.Kind.STEP)
                up == 1 && down == 1 ->
                    Relationship(gendered(sex, "Brother-in-law", "Sister-in-law", "Sibling-in-law"), kind = Relationship.Kind.IN_LAW)
                up == 0 && down == 1 ->
                    Relationship(gendered(sex, "Son-in-law", "Daughter-in-law", "Child-in-law"), kind = Relationship.Kind.IN_LAW)
                up == 2 && down == 1 -> // aunt's husband = uncle (by marriage)
                    Relationship(gendered(sex, "Uncle (by marriage)", "Aunt (by marriage)", "Aunt/Uncle (by marriage)"), kind = Relationship.Kind.SPOUSE_OF_RELATIVE)
                else -> {
                    val relSex = tree.people[spouseOfTarget]?.sex ?: Sex.U
                    val spouseWord = gendered(sex, "Husband", "Wife", "Spouse")
                    Relationship("$spouseWord of ${bloodLabel(up, down, relSex, false).lowercase()}", kind = Relationship.Kind.SPOUSE_OF_RELATIVE)
                }
            }
        }
        return null
    }

    /**
     * Breadth-first walk up the tree: ancestor -> generations above [id].
     * Parent families are included as nodes ("F:" + id) so siblings whose parents
     * are not named in the file still share a common ancestor.
     */
    private fun ancestorsWithDepth(id: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        out[id] = 0
        var frontier = listOf(id)
        var depth = 0
        while (frontier.isNotEmpty() && depth < 40) {
            depth++
            val next = mutableListOf<String>()
            for (p in frontier) for (famId in tree.people[p]?.parentFamilyIds.orEmpty()) {
                val famKey = "F:$famId"
                if (famKey !in out) out[famKey] = depth
                for (parent in tree.families[famId]?.spouseIds.orEmpty()) {
                    if (parent !in out) { out[parent] = depth; next += parent }
                }
            }
            frontier = next
        }
        return out
    }

    /** Returns (up, down) via the closest common ancestor, or null if not blood related. */
    private fun blood(fromId: String, targetId: String, fromAncestors: Map<String, Int>): Pair<Int, Int>? {
        val targetAnc = ancestorsWithDepth(targetId)
        var best: Pair<Int, Int>? = null
        for ((anc, down) in targetAnc) {
            val up = fromAncestors[anc] ?: continue
            if (best == null || up + down < best.first + best.second) best = up to down
        }
        return best
    }

    private fun isHalf(home: String, target: String, up: Int, down: Int): Boolean {
        if (up != 1 || down != 1) return false
        val a = tree.parentsOf(home).toSet()
        val b = tree.parentsOf(target).toSet()
        return a.size == 2 && b.size == 2 && a != b
    }

    enum class LinkType { PARENT, CHILD, SIBLING, SPOUSE }

    /** One link in the chain: [fromId] is the [link] of [toId], e.g. "Randal" is the "son" of "George". */
    data class PathStep(val fromId: String, val toId: String, val type: LinkType, val link: String)

    /**
     * The chain of people from the home person to [targetId], each step being a parent,
     * child, sibling or spouse. It climbs to the shared ancestor and comes back down;
     * marriage links are used only when needed. Returns null if there is no home person or no connection.
     */
    fun path(targetId: String): List<PathStep>? {
        val home = homeId ?: return null
        if (tree.people[home] == null || tree.people[targetId] == null) return null
        if (home == targetId) return emptyList()

        val dist = HashMap<String, Double>()
        val prev = HashMap<String, Pair<String, LinkType>>()
        val queue = java.util.PriorityQueue<Pair<Double, String>>(compareBy { it.first })
        dist[home] = 0.0
        queue.add(0.0 to home)
        var expanded = 0
        while (queue.isNotEmpty() && expanded < 200_000) {
            val (d, u) = queue.poll()
            if (d > (dist[u] ?: Double.MAX_VALUE)) continue
            if (u == targetId) break
            expanded++
            fun relax(v: String, type: LinkType, w: Double) {
                val nd = d + w
                if (nd < (dist[v] ?: Double.MAX_VALUE)) {
                    dist[v] = nd; prev[v] = u to type; queue.add(nd to v)
                }
            }
            tree.parentsOf(u).forEach { relax(it, LinkType.PARENT, 1.0) }
            tree.childrenOf(u).forEach { relax(it, LinkType.CHILD, 1.0) }
            // Going up to the shared parent and back down costs 2, so a sibling link (2.1)
            // is only used when the parents aren't named in the file.
            tree.siblingsOf(u).forEach { relax(it, LinkType.SIBLING, 2.1) }
            tree.spousesOf(u).forEach { relax(it, LinkType.SPOUSE, 2.5) }
        }
        if (targetId !in prev) return null

        // Walk back from the target, then describe each link from the earlier person's side
        val steps = mutableListOf<PathStep>()
        var cur = targetId
        while (cur != home) {
            val (before, type) = prev.getValue(cur)
            val sex = tree.people[before]?.sex ?: Sex.U
            val word = when (type) {
                LinkType.PARENT -> gendered(sex, "son", "daughter", "child")     // cur is before's parent
                LinkType.CHILD -> gendered(sex, "father", "mother", "parent")    // cur is before's child
                LinkType.SIBLING -> gendered(sex, "brother", "sister", "sibling")
                LinkType.SPOUSE -> gendered(sex, "husband", "wife", "spouse")
            }
            steps += PathStep(before, cur, type, word)
            cur = before
        }
        return steps.reversed()
    }

    companion object {
        fun gendered(sex: Sex, m: String, f: String, u: String) = when (sex) { Sex.M -> m; Sex.F -> f; Sex.U -> u }

        private fun greats(n: Int): String = when {
            n <= 0 -> ""
            n == 1 -> "Great-"
            n == 2 -> "Great-great-"
            else -> "${ordinal(n)} great-"
        }

        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            return "$n$suffix"
        }

        private fun times(n: Int): String = when (n) {
            1 -> "once"; 2 -> "twice"; 3 -> "three times"; else -> "$n times"
        }

        fun bloodLabel(up: Int, down: Int, sex: Sex, half: Boolean): String {
            return when {
                up == 0 && down == 0 -> "Self"
                down == 0 -> when (up) {          // direct ancestor
                    1 -> gendered(sex, "Father", "Mother", "Parent")
                    2 -> gendered(sex, "Grandfather", "Grandmother", "Grandparent")
                    else -> greats(up - 2) + gendered(sex, "grandfather", "grandmother", "grandparent")
                }
                up == 0 -> when (down) {          // direct descendant
                    1 -> gendered(sex, "Son", "Daughter", "Child")
                    2 -> gendered(sex, "Grandson", "Granddaughter", "Grandchild")
                    else -> greats(down - 2) + gendered(sex, "grandson", "granddaughter", "grandchild")
                }
                up == 1 && down == 1 ->
                    if (half) gendered(sex, "Half-brother", "Half-sister", "Half-sibling")
                    else gendered(sex, "Brother", "Sister", "Sibling")
                down == 1 -> {                    // aunts / uncles
                    val w = gendered(sex, "uncle", "aunt", "aunt/uncle")
                    when (val g = up - 2) {
                        0 -> w.replaceFirstChar { it.uppercase() }
                        1 -> "Great-$w"
                        2 -> "Great-great-$w"
                        else -> "${ordinal(g)} great-$w"
                    }
                }
                up == 1 -> when (down) {          // nieces / nephews
                    2 -> gendered(sex, "Nephew", "Niece", "Niece/Nephew")
                    3 -> gendered(sex, "Grandnephew", "Grandniece", "Grandniece/nephew")
                    else -> greats(down - 3) + gendered(sex, "grandnephew", "grandniece", "grandniece/nephew")
                }
                else -> {                         // cousins
                    val degree = minOf(up, down) - 1
                    val removed = kotlin.math.abs(up - down)
                    val base = "${ordinal(degree)} cousin"
                    if (removed == 0) base else "$base ${times(removed)} removed"
                }
            }
        }
    }
}
