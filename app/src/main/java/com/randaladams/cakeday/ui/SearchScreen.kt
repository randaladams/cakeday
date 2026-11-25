package com.randaladams.cakeday.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.model.Person
import java.text.Normalizer

private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

fun searchPeople(s: TreeState, query: String): List<Person> {
    val tokens = norm(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    return s.tree.people.values.asSequence()
        .filter { p ->
            val hay = norm(p.fullName + " " + p.lifespan())
            tokens.all { hay.contains(it) }
        }
        .sortedBy { it.sortName }
        .take(300)
        .toList()
}

/** Search tab. [onPick] is called with the chosen person's id. */
@Composable
fun SearchView(vm: MainViewModel, s: TreeState, onPick: (String) -> Unit) {
    val results = remember(s, vm.searchQuery) { searchPeople(s, vm.searchQuery) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Search by name or year (e.g. \"John Smith 1920\")") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.searchQuery.isNotEmpty()) IconButton(onClick = { vm.searchQuery = "" }) { Icon(Icons.Default.Clear, "Clear") }
            },
            singleLine = true
        )
        PeopleList(s, results, vm.searchQuery.isBlank(), onPick)
    }
}

@Composable
private fun PeopleList(s: TreeState, results: List<Person>, blank: Boolean, onPick: (String) -> Unit) {
    if (blank) {
        Text(
            "${s.tree.people.size} people in your tree. Start typing a name.",
            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline
        )
        return
    }
    if (results.isEmpty()) {
        Text("No matches", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { p -> PersonRow(s, p) { onPick(p.id) } }
    }
}

@Composable
fun PersonRow(s: TreeState, p: Person, onClick: () -> Unit) {
    val living = s.living.isLiving(p.id)
    val rel = s.relationships.relationship(p.id)?.label
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.fullName, fontWeight = FontWeight.SemiBold)
                val sub = listOfNotNull(p.lifespan().ifBlank { null }, rel).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (living) "Living" else "Deceased",
                style = MaterialTheme.typography.labelSmall,
                color = if (living) LivingColor else DeceasedColor
            )
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickHomeScreen(vm: MainViewModel, s: TreeState) {
    var q by rememberSaveable { mutableStateOf("") }
    val results = remember(s, q) { searchPeople(s, q) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Choose home person") },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                "Relationships (cousin, great-grandfather, …) are shown relative to this person — usually you.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Search by name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            PeopleList(s, results, q.isBlank()) { id ->
                vm.setHomePerson(id)
                vm.pop()
            }
        }
    }
}
