package com.randaladams.cakeday.logic

import com.randaladams.cakeday.model.AppSettings
import com.randaladams.cakeday.model.PeopleScope
import com.randaladams.cakeday.model.RelativeScope

/** Applies the notification settings to a day's events. */
object NotificationFilter {
    fun filter(
        events: List<Occurrence>,
        s: AppSettings,
        relationships: RelationshipCalculator
    ): List<Occurrence> = events.filter { o ->
        val scopeOk = when (s.notifyScope) {
            PeopleScope.ALL -> true
            PeopleScope.LIVING -> o.living
            PeopleScope.DECEASED -> !o.living
        }
        val kindOk = when (o.event.kind) {
            EventKind.BIRTHDAY -> s.notifyBirthdays
            EventKind.ANNIVERSARY -> s.notifyAnniversaries
            EventKind.DEATH, EventKind.BURIAL -> s.notifyDeaths
            EventKind.OTHER -> s.notifyOtherEvents
            EventKind.CUSTOM -> s.notifyCustomEvents
        }
        // Milestones only apply to past events; a living person's birthday counts too
        val milestoneOk = !s.notifyMilestonesOnly || o.isMilestone || o.event.kind == EventKind.CUSTOM
        val relOk = when (s.notifyRelatives) {
            RelativeScope.EVERYONE -> true
            else -> o.event.personIds.any { id ->
                val r = relationships.relationship(id)
                when (s.notifyRelatives) {
                    RelativeScope.RELATED -> r != null
                    RelativeScope.BLOOD -> r?.isBlood == true
                    RelativeScope.CLOSE -> r?.isClose == true
                    RelativeScope.EVERYONE -> true
                }
            }
        }
        scopeOk && kindOk && milestoneOk && relOk
    }
}
