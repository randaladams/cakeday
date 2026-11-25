package com.randaladams.cakeday.model

enum class PeopleScope(val label: String) {
    ALL("Living and deceased"),
    LIVING("Living only"),
    DECEASED("Deceased only")
}

enum class RelativeScope(val label: String) {
    EVERYONE("Everyone in the tree"),
    RELATED("Anyone related to the home person"),
    BLOOD("Blood relatives only"),
    CLOSE("Close family (up to first cousins & in-laws)")
}

data class AppSettings(
    val homePersonId: String? = null,
    val maxAge: Int = 105,
    val unknownIsLiving: Boolean = false,
    val includeApproximateDates: Boolean = true,

    // notifications
    val notificationsEnabled: Boolean = false,
    val notifyHour: Int = 8,
    val notifyMinute: Int = 0,
    val notifyScope: PeopleScope = PeopleScope.ALL,
    val notifyBirthdays: Boolean = true,
    val notifyAnniversaries: Boolean = true,
    val notifyDeaths: Boolean = true,
    val notifyOtherEvents: Boolean = false,
    val notifyCustomEvents: Boolean = true,
    val notifyMilestonesOnly: Boolean = false,
    val notifyRelatives: RelativeScope = RelativeScope.EVERYONE,
    /** Extra heads-up this many days before (0 = off). */
    val notifyDaysAhead: Int = 0,
    val notifyWhenEmpty: Boolean = false
)
