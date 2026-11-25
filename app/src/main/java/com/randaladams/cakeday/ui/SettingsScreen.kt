package com.randaladams.cakeday.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.logic.NotificationFilter
import com.randaladams.cakeday.model.AppSettings
import com.randaladams.cakeday.model.PeopleScope
import com.randaladams.cakeday.model.RelativeScope
import com.randaladams.cakeday.notify.Notifier
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, s: TreeState, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val set = s.settings
    fun update(t: (AppSettings) -> AppSettings) = vm.updateSettings(t)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importGed(it) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) update { it.copy(notificationsEnabled = true) }
        else vm.message = "Notifications are blocked. You can allow them in Android Settings."
    }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {

            // ---------------- Family tree
            Heading("Family tree")
            if (s.hasTree) {
                Text(s.fileName ?: "Imported tree")
                Text("${s.tree.people.size} people · ${s.tree.families.size} families", style = MaterialTheme.typography.bodySmall)
            } else Text("No tree imported yet")
            Spacer(Modifier.height(8.dp))
            if (vm.importing) CircularProgressIndicator()
            else Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(if (s.hasTree) "Import a different .ged file" else "Import .ged file")
            }
            Text(
                "Cakeday reads the file and keeps a private copy. The original file is never changed. " +
                    "Your edits are kept separately and re-applied when you import an updated file.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp)
            )

            // ---------------- Home person
            Heading("Home person")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.homePerson?.fullName ?: "Not set", Modifier.weight(1f))
                OutlinedButton(onClick = { vm.push(Screen.PickHomePerson) }, enabled = s.hasTree) { Text("Change") }
            }

            // ---------------- Living
            Heading("Who counts as living")
            var age by remember(set.maxAge) { mutableFloatStateOf(set.maxAge.toFloat()) }
            Text("Treat people without a death record as living until age ${age.toInt()}")
            Slider(
                value = age, onValueChange = { age = it }, valueRange = 80f..120f, steps = 39,
                onValueChangeFinished = { update { it.copy(maxAge = age.toInt()) } }
            )
            SwitchRow("If there are no dates at all, assume living", set.unknownIsLiving) { v -> update { it.copy(unknownIsLiving = v) } }
            SwitchRow("Include approximate dates (ABT, EST, BEF…)", set.includeApproximateDates) { v -> update { it.copy(includeApproximateDates = v) } }
            Text(
                "Birth years are estimated from parents, children and spouses when missing. " +
                    "You can override any person from their Edit screen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
            )

            // ---------------- Notifications
            Heading("Daily notification")
            SwitchRow("Send a daily summary", set.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else update { it.copy(notificationsEnabled = on) }
            }
            if (set.notificationsEnabled) {
                val time = LocalTime.of(set.notifyHour, set.notifyMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                Row(
                    Modifier.fillMaxWidth().clickable {
                        TimePickerDialog(ctx, { _, h, m -> update { it.copy(notifyHour = h, notifyMinute = m) } },
                            set.notifyHour, set.notifyMinute, DateFormat.is24HourFormat(ctx)).show()
                    }.padding(vertical = 10.dp)
                ) {
                    Text("Time", Modifier.weight(1f)); Text(time, color = MaterialTheme.colorScheme.primary)
                }

                SubHeading("People")
                PeopleScope.entries.forEach { sc ->
                    RadioRow(sc.label, set.notifyScope == sc) { update { it.copy(notifyScope = sc) } }
                }

                SubHeading("Event types")
                SwitchRow("🎂 Birthdays", set.notifyBirthdays) { v -> update { it.copy(notifyBirthdays = v) } }
                SwitchRow("💍 Wedding anniversaries", set.notifyAnniversaries) { v -> update { it.copy(notifyAnniversaries = v) } }
                SwitchRow("🕯️ Death & burial anniversaries", set.notifyDeaths) { v -> update { it.copy(notifyDeaths = v) } }
                SwitchRow("📜 Other life events (christenings, immigration…)", set.notifyOtherEvents) { v -> update { it.copy(notifyOtherEvents = v) } }
                SwitchRow("⭐ My own events", set.notifyCustomEvents) { v -> update { it.copy(notifyCustomEvents = v) } }
                SwitchRow("Milestones only (1st, 5th, 10th, 25th, 50th…)", set.notifyMilestonesOnly) { v -> update { it.copy(notifyMilestonesOnly = v) } }

                SubHeading("Relatives")
                RelativeScope.entries.forEach { sc ->
                    RadioRow(sc.label, set.notifyRelatives == sc, enabled = sc == RelativeScope.EVERYONE || s.homePerson != null) {
                        update { it.copy(notifyRelatives = sc) }
                    }
                }
                if (s.homePerson == null) Text("Set a home person to filter by relationship.", style = MaterialTheme.typography.bodySmall)

                SubHeading("Advance reminder")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 1 to "1 day", 3 to "3 days", 7 to "1 week", 14 to "2 weeks").forEach { (d, l) ->
                        FilterChip(selected = set.notifyDaysAhead == d, onClick = { update { it.copy(notifyDaysAhead = d) } }, label = { Text(l) })
                    }
                }
                SwitchRow("Notify even when there's nothing today", set.notifyWhenEmpty) { v -> update { it.copy(notifyWhenEmpty = v) } }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val today = LocalDate.now()
                    val t = NotificationFilter.filter(s.engine.on(today), set, s.relationships)
                    val a = if (set.notifyDaysAhead > 0)
                        NotificationFilter.filter(s.engine.on(today.plusDays(set.notifyDaysAhead.toLong())), set, s.relationships)
                    else emptyList()
                    Notifier.show(ctx, t, a, set.notifyDaysAhead, whenEmpty = true)
                }) { Text("Send a test notification now") }
            }

            // ---------------- Edits
            Heading("My edits")
            val editCount = s.edits.persons.size + s.edits.customEvents.size + s.edits.families.size
            Text("$editCount saved changes")
            TextButton(onClick = { confirmClear = true }, enabled = editCount > 0) {
                Text("Clear all edits", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all edits?") },
        text = { Text("Every change and added event will be removed. The tree goes back to exactly what's in your .ged file.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearAllEdits() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun Heading(t: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Text(t, Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SubHeading(t: String) =
    Text(t, Modifier.padding(top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
    }
}
