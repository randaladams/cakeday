package com.randaladams.cakeday.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.logic.EventKind
import com.randaladams.cakeday.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(vm: MainViewModel, s: TreeState, id: String, snackbar: SnackbarHostState) {
    val p = s.tree.person(id)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p?.fullName ?: "Person") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (p != null) IconButton(onClick = { vm.push(Screen.EditPerson(id)) }) { Icon(Icons.Default.Edit, "Edit") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        if (p == null) {
            Text("This person is no longer in the tree.", Modifier.padding(pad).padding(16.dp)); return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item { Header(vm, s, p) }
            if (s.living.isLiving(p.id)) item { ContactSection(s, p) }
            upcoming(s, p)
            lifeEvents(p)
            family(vm, s, p)
            if (p.notes.isNotBlank()) {
                item { SectionTitle("Notes") }
                item { Text(p.notes, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun Header(vm: MainViewModel, s: TreeState, p: Person) {
    val st = s.living.status(p.id)
    val rel = s.relationships.relationship(p.id)
    Column(Modifier.padding(16.dp)) {
        Text(p.fullName, style = MaterialTheme.typography.headlineSmall)
        val span = p.lifespan()
        if (span.isNotEmpty()) Text(span, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.push(Screen.EditPerson(p.id)) }, label = {
                Text(
                    (if (st.living) "Living" else "Deceased") +
                        if (st.calculated) " (calculated)" else ""
                )
            })
            if (p.edited) AssistChip(onClick = { vm.push(Screen.EditPerson(p.id)) }, label = { Text("Edited") })
        }
        when {
            rel != null -> Text(
                if (rel.kind == com.randaladams.cakeday.logic.Relationship.Kind.SELF) rel.label
                else "${rel.label} of ${s.homePerson?.fullName}",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
            )
            s.homePerson != null -> Text("No family connection to ${s.homePerson?.fullName} found", style = MaterialTheme.typography.bodySmall)
        }
        if (rel != null && rel.kind != com.randaladams.cakeday.logic.Relationship.Kind.SELF) {
            OutlinedButton(onClick = { vm.push(Screen.RelationshipPath(p.id)) }, Modifier.padding(top = 6.dp)) {
                Text("How are we related?")
            }
        }
        if (s.settings.homePersonId != p.id) {
            TextButton(onClick = { vm.setHomePerson(p.id) }) {
                Icon(Icons.Default.Home, null); Spacer(Modifier.width(6.dp)); Text("Make home person")
            }
        }
    }
}

@Composable
private fun ContactSection(s: TreeState, p: Person) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var matches by remember(p.id) { mutableStateOf<List<ContactMatch>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val todays = s.engine.on(LocalDate.now()).filter { p.id in it.event.personIds && it.living }
    val greeting = when (todays.firstOrNull()?.event?.kind) {
        EventKind.BIRTHDAY -> "Happy birthday, ${p.givenName.substringBefore(' ')}! 🎂"
        EventKind.ANNIVERSARY -> "Happy anniversary! 💍"
        else -> null
    }

    fun lookup() {
        loading = true
        scope.launch {
            matches = withContext(Dispatchers.IO) { runCatching { ContactLookup.find(ctx, p) }.getOrDefault(emptyList()) }
            loading = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lookup() else matches = emptyList()
    }

    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Get in touch", style = MaterialTheme.typography.titleSmall)
            if (p.phone.isNotBlank()) PhoneRow(p.phone, "Saved in Cakeday", greeting)
            when {
                loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                matches == null -> OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) lookup()
                    else permission.launch(Manifest.permission.READ_CONTACTS)
                }) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Find in my contacts") }
                matches!!.isEmpty() -> Text("No matching contacts found (or permission not given).", style = MaterialTheme.typography.bodySmall)
                else -> matches!!.forEach { m ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).clickable { Actions.openContact(ctx, m.contactId, m.lookupKey) })
                        if (!m.strong) Text("possible match", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (m.numbers.isEmpty()) Text("No phone number", style = MaterialTheme.typography.bodySmall)
                    m.numbers.forEach { n -> PhoneRow(n.number, n.type, greeting) }
                }
            }
        }
    }
}

@Composable
private fun PhoneRow(number: String, type: String, greeting: String?) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(number)
            Text(type, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = { Actions.dial(ctx, number) }) { Icon(Icons.Default.Call, "Call") }
        IconButton(onClick = { Actions.text(ctx, number, greeting) }) { Icon(Icons.AutoMirrored.Filled.Send, "Text") }
    }
}

private fun LazyListScope.upcoming(s: TreeState, p: Person) {
    val events = s.engine.allEvents.filter { p.id in it.personIds }
    if (events.isEmpty()) return
    val today = LocalDate.now()
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    item { SectionTitle("Yearly events") }
    events.map { it to s.engine.nextOccurrence(it, today) }.sortedBy { it.second }.forEach { (e, next) ->
        item {
            val ctx = LocalContext.current
            val occ = s.engine.on(next).firstOrNull { it.event == e }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.kind.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(occ?.headline ?: e.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (next == today) "Today" else "Next: ${next.format(fmt)}") +
                            if (e.personIds.size > 1) " · with ${occ?.names?.split(" & ")?.firstOrNull { it != p.fullName } ?: ""}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { Actions.addToCalendar(ctx, s, e) }) {
                    Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(4.dp)); Text("Calendar")
                }
            }
        }
    }
}

private fun LazyListScope.lifeEvents(p: Person) {
    if (p.events.isEmpty()) return
    item { SectionTitle("Life events") }
    // Merge copies of the same event (same type and date) that came from different sources
    val groups = p.events.groupBy { "${it.tag}|${it.label}|${it.date?.display()}" }.values
    groups.forEach { copies ->
        val e = copies.first()
        val places = copies.mapNotNull { it.place }
            .distinctBy { it.lowercase().replace(Regex("[^a-z]"), "") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("${e.label}: ${e.date?.display() ?: "date unknown"}")
                places.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (places.size > 1) Text(
                    "Also recorded as: " + places.drop(1).joinToString("; "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun LazyListScope.family(vm: MainViewModel, s: TreeState, p: Person) {
    val t = s.tree
    fun group(title: String, ids: List<String>, extra: (String) -> String? = { null }) {
        val people = ids.mapNotNull { t.person(it) }
        if (people.isEmpty()) return
        item { SectionTitle(title) }
        people.forEach { rp ->
            item {
                Column(Modifier.fillMaxWidth().clickable { vm.openPerson(rp.id) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(rp.fullName, fontWeight = FontWeight.SemiBold)
                    val sub = listOfNotNull(rp.lifespan().ifBlank { null }, extra(rp.id), s.relationships.relationship(rp.id)?.label).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
        }
    }
    group("Parents", t.parentsOf(p.id))
    group("Spouses", t.spousesOf(p.id)) { sid ->
        p.spouseFamilyIds.mapNotNull { t.families[it] }.firstOrNull { it.otherSpouse(p.id) == sid }?.let { f ->
            listOfNotNull(
                f.marriage?.date?.let { "married ${it.display()}" },
                if (f.isDivorced) "divorced" else null
            ).joinToString(", ").ifBlank { null }
        }
    }
    group("Siblings", t.siblingsOf(p.id))
    group("Children", t.childrenOf(p.id))
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
}
