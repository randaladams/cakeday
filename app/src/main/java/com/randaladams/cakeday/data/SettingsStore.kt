package com.randaladams.cakeday.data

import android.content.Context
import com.randaladams.cakeday.model.AppSettings

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("cakeday_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            homePersonId = prefs.getString("homePersonId", null),
            maxAge = prefs.getInt("maxAge", d.maxAge),
            unknownIsLiving = prefs.getBoolean("unknownIsLiving", d.unknownIsLiving),
            includeApproximateDates = prefs.getBoolean("includeApproximateDates", d.includeApproximateDates),
            notificationsEnabled = prefs.getBoolean("notificationsEnabled", d.notificationsEnabled),
            notifyHour = prefs.getInt("notifyHour", d.notifyHour),
            notifyMinute = prefs.getInt("notifyMinute", d.notifyMinute),
            notifyScope = enumOr(prefs.getString("notifyScope", null), d.notifyScope),
            notifyBirthdays = prefs.getBoolean("notifyBirthdays", d.notifyBirthdays),
            notifyAnniversaries = prefs.getBoolean("notifyAnniversaries", d.notifyAnniversaries),
            notifyDeaths = prefs.getBoolean("notifyDeaths", d.notifyDeaths),
            notifyOtherEvents = prefs.getBoolean("notifyOtherEvents", d.notifyOtherEvents),
            notifyCustomEvents = prefs.getBoolean("notifyCustomEvents", d.notifyCustomEvents),
            notifyMilestonesOnly = prefs.getBoolean("notifyMilestonesOnly", d.notifyMilestonesOnly),
            notifyRelatives = enumOr(prefs.getString("notifyRelatives", null), d.notifyRelatives),
            notifyDaysAhead = prefs.getInt("notifyDaysAhead", d.notifyDaysAhead),
            notifyWhenEmpty = prefs.getBoolean("notifyWhenEmpty", d.notifyWhenEmpty)
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putString("homePersonId", s.homePersonId)
            .putInt("maxAge", s.maxAge)
            .putBoolean("unknownIsLiving", s.unknownIsLiving)
            .putBoolean("includeApproximateDates", s.includeApproximateDates)
            .putBoolean("notificationsEnabled", s.notificationsEnabled)
            .putInt("notifyHour", s.notifyHour)
            .putInt("notifyMinute", s.notifyMinute)
            .putString("notifyScope", s.notifyScope.name)
            .putBoolean("notifyBirthdays", s.notifyBirthdays)
            .putBoolean("notifyAnniversaries", s.notifyAnniversaries)
            .putBoolean("notifyDeaths", s.notifyDeaths)
            .putBoolean("notifyOtherEvents", s.notifyOtherEvents)
            .putBoolean("notifyCustomEvents", s.notifyCustomEvents)
            .putBoolean("notifyMilestonesOnly", s.notifyMilestonesOnly)
            .putString("notifyRelatives", s.notifyRelatives.name)
            .putInt("notifyDaysAhead", s.notifyDaysAhead)
            .putBoolean("notifyWhenEmpty", s.notifyWhenEmpty)
            .apply()
    }

    var treeFileName: String?
        get() = prefs.getString("treeFileName", null)
        set(v) = prefs.edit().putString("treeFileName", v).apply()

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
