package com.randaladams.cakeday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.logic.Occurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
private val SHORT_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")
private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy")

// ---------------------------------------------------------------- Day

@Composable
fun DayView(vm: MainViewModel, s: TreeState) {
    val date = vm.dayDate
    val events = remember(s, date) { s.engine.on(date) }
    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            title = date.format(DAY_FMT),
            onPrev = { vm.dayDate = date.minusDays(1) },
            onNext = { vm.dayDate = date.plusDays(1) },
            onToday = if (date != LocalDate.now()) ({ vm.dayDate = LocalDate.now() }) else null
        )
        HomeBanner(vm, s)
        TwoPanels(vm, s, events, showDates = false, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Week

@Composable
fun WeekView(vm: MainViewModel, s: TreeState) {
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val start = vm.weekDate.with(TemporalAdjusters.previousOrSame(firstDay))
    val end = start.plusDays(6)
    val events = remember(s, start) { s.engine.between(start, end) }
    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            title = "${start.format(SHORT_FMT)} – ${end.format(SHORT_FMT)}",
            onPrev = { vm.weekDate = start.minusWeeks(1) },
            onNext = { vm.weekDate = start.plusWeeks(1) },
            onToday = if (LocalDate.now() !in start..end) ({ vm.weekDate = LocalDate.now() }) else null
        )
        HomeBanner(vm, s)
        TwoPanels(vm, s, events, showDates = true, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Month

@Composable
fun MonthView(vm: MainViewModel, s: TreeState) {
    val selected = vm.monthDate
    val first = selected.withDayOfMonth(1)
    val last = selected.with(TemporalAdjusters.lastDayOfMonth())
    val monthEvents = remember(s, first) { s.engine.between(first, last).groupBy { it.on } }
    val dayEvents = monthEvents[selected].orEmpty()
    val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            title = first.format(MONTH_FMT),
            onPrev = { vm.monthDate = first.minusMonths(1) },
            onNext = { vm.monthDate = first.plusMonths(1) },
            onToday = if (selected != LocalDate.now()) ({ vm.monthDate = LocalDate.now() }) else null
        )
        // weekday header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            (0 until 7).forEach { i ->
                val dow: DayOfWeek = firstDow.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        val lead = ((first.dayOfWeek.value - firstDow.value) + 7) % 7
        val cells = lead + last.dayOfMonth
        val rows = (cells + 6) / 7
        Column(Modifier.padding(horizontal = 8.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val idx = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                            if (idx in 1..last.dayOfMonth) {
                                val d = first.withDayOfMonth(idx)
                                val evs = monthEvents[d].orEmpty()
                                DayCell(
                                    day = idx,
                                    selected = d == selected,
                                    today = d == LocalDate.now(),
                                    living = evs.count { it.living },
                                    deceased = evs.count { !it.living },
                                    onClick = { vm.monthDate = d }
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            selected.format(DAY_FMT),
            Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
            style = MaterialTheme.typography.titleSmall
        )
        TwoPanels(vm, s, dayEvents, showDates = false, modifier = Modifier.weight(1f), compact = true)
    }
}

@Composable
private fun DayCell(day: Int, selected: Boolean, today: Boolean, living: Int, deceased: Int, onClick: () -> Unit) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        today -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.size(38.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$day", color = fg, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (living > 0) Dot(LivingColor)
            if (deceased > 0) Dot(DeceasedColor)
        }
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(5.dp).clip(CircleShape).background(color))

// ---------------------------------------------------------------- shared pieces

@Composable
fun PeriodHeader(title: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
        Text(
            title, Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (onToday != null) TextButton(onClick = onToday) { Text("Today") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
    }
}

@Composable
private fun HomeBanner(vm: MainViewModel, s: TreeState) {
    if (s.homePerson != null) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { vm.push(Screen.PickHomePerson) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Text(
            "Tap to choose your home person — then every event shows how that person is related to you.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
        )
    }
}

/** The two panels: living people on top, people no longer living below. */
@Composable
fun TwoPanels(
    vm: MainViewModel, s: TreeState, events: List<Occurrence>, showDates: Boolean,
    modifier: Modifier = Modifier, compact: Boolean = false
) {
    val living = events.filter { it.living }
    val deceased = events.filterNot { it.living }
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EventPanel(vm, s, "Celebrate", "Living", living, LivingColor, showDates, Modifier.weight(1f), compact)
        EventPanel(vm, s, "Remember", "No longer living", deceased, DeceasedColor, showDates, Modifier.weight(1f), compact)
    }
}

@Composable
private fun EventPanel(
    vm: MainViewModel, s: TreeState, title: String, subtitle: String, events: List<Occurrence>,
    accent: Color, showDates: Boolean, modifier: Modifier, compact: Boolean
) {
    Card(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(accent.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("  ·  $subtitle", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("${events.size}", style = MaterialTheme.typography.titleSmall, color = accent)
        }
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (showDates) {
                    events.groupBy { it.on }.forEach { (date, list) ->
                        item(key = "h$date") {
                            Text(
                                if (date == LocalDate.now()) "Today · ${date.format(SHORT_FMT)}" else date.format(SHORT_FMT),
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelLarge, color = accent
                            )
                        }
                        items(list) { o -> EventRow(vm, s, o, compact) }
                    }
                } else {
                    items(events) { o -> EventRow(vm, s, o, compact) }
                }
            }
        }
    }
}

@Composable
fun EventRow(vm: MainViewModel, s: TreeState, o: Occurrence, compact: Boolean = false) {
    val ctx = LocalContext.current
    val rel = o.event.personIds.firstNotNullOfOrNull { id ->
        s.relationships.relationship(id)?.let { r ->
            if (o.event.personIds.size > 1) "${s.tree.person(id)?.givenName}: ${r.label}" else r.label
        }
    }
    // open the person most closely related to home; otherwise the first
    val target = o.event.personIds.minByOrNull { id ->
        s.relationships.relationship(id)?.let { if (it.up >= 0) it.up + it.down else 50 } ?: 100
    } ?: o.event.personIds.first()
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { vm.openPerson(target) }
                .padding(start = 12.dp, end = 4.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(o.event.kind.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(o.names, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(o.headline, style = MaterialTheme.typography.bodyMedium)
                if (rel != null) Text(rel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { Actions.addToCalendar(ctx, s, o.event) }) {
                Icon(Icons.Default.DateRange, contentDescription = "Add to calendar", tint = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(Modifier.padding(start = 48.dp))
    }
}
