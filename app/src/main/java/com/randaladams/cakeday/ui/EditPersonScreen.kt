package com.randaladams.cakeday.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.model.CustomEvent
import com.randaladams.cakeday.model.FamilyEdit
import com.randaladams.cakeday.model.GedDate
import com.randaladams.cakeday.model.PersonEdit
import com.randaladams.cakeday.model.Sex
import java.util.UUID

private class CustomRow(val id: String, label: String, date: String) {
    var label by mutableStateOf(label)
    var date by mutableStateOf(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPersonScreen(vm: MainViewModel, s: TreeState, id: String) {
    val p = s.tree.person(id)
    val orig = s.originalTree.person(id)
    if (p == null || orig == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val existing = s.edits.persons[id] ?: PersonEdit()

    var given by remember { mutableStateOf(p.givenName) }
    var surname by remember { mutableStateOf(p.surname) }
    var sex by remember { mutableStateOf(p.sex) }
    var birthDate by remember { mutableStateOf(p.birth?.date?.raw ?: "") }
    var birthPlace by remember { mutableStateOf(p.birth?.place ?: "") }
    var deathDate by remember { mutableStateOf(p.death?.date?.raw ?: "") }
    var deathPlace by remember { mutableStateOf(p.death?.place ?: "") }
    var living by remember { mutableStateOf(existing.livingOverride) }
    var phone by remember { mutableStateOf(p.phone) }
    var notes by remember { mutableStateOf(p.notes) }
    val customs = remember { mutableStateListOf(*s.edits.customEvents.filter { it.personId == id }.map { CustomRow(it.id, it.label, it.date) }.toTypedArray()) }
    val spouseFams = p.spouseFamilyIds.mapNotNull { s.tree.families[it] }
    val marriage = remember { mutableStateMapOf<String, String>().apply { spouseFams.forEach { put(it.id, it.marriage?.date?.raw ?: "") } } }
    val divorced = remember { mutableStateMapOf<String, Boolean>().apply { spouseFams.forEach { put(it.id, it.isDivorced) } } }
    var confirmRevert by remember { mutableStateOf(false) }

    fun save() {
        val oBirth = orig.birth; val oDeath = orig.death
        fun changed(new: String, old: String?) = if (new.trim() == (old ?: "").trim()) null else new.trim()
        val edit = PersonEdit(
            givenName = changed(given, orig.givenName),
            surname = changed(surname, orig.surname),
            sex = if (sex == orig.sex) null else sex,
            birthDate = changed(birthDate, oBirth?.date?.raw),
            birthPlace = changed(birthPlace, oBirth?.place),
            deathDate = changed(deathDate, oDeath?.date?.raw),
            deathPlace = changed(deathPlace, oDeath?.place),
            livingOverride = living,
            phone = changed(phone, ""),
            notes = changed(notes, orig.notes)
        )
        val famEdits = spouseFams.associate { f ->
            val of = s.originalTree.families[f.id]
            val md = marriage[f.id].orEmpty()
            val fe = FamilyEdit(
                marriageDate = changed(md, of?.marriage?.date?.raw),
                divorced = divorced[f.id].takeIf { it != of?.isDivorced }
            )
            f.id to (if (fe == FamilyEdit()) null else fe)
        }
        val ce = customs.filter { it.label.isNotBlank() || it.date.isNotBlank() }
            .map { CustomEvent(it.id, id, it.label.trim(), it.date.trim()) }
        vm.saveEdits(id, edit, famEdits, ce)
        vm.pop()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Edit ${p.givenName}") },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
            actions = { TextButton(onClick = { save() }) { Text("Save") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Changes are stored in Cakeday only. Your original .ged file is never modified.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
            )
            OutlinedTextField(given, { given = it }, Modifier.fillMaxWidth(), label = { Text("Given names") }, singleLine = true)
            OutlinedTextField(surname, { surname = it }, Modifier.fillMaxWidth(), label = { Text("Surname") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sex")
                listOf(Sex.M to "Male", Sex.F to "Female", Sex.U to "Unknown").forEach { (v, l) ->
                    FilterChip(selected = sex == v, onClick = { sex = v }, label = { Text(l) })
                }
            }
            DateField("Birth date", birthDate) { birthDate = it }
            OutlinedTextField(birthPlace, { birthPlace = it }, Modifier.fillMaxWidth(), label = { Text("Birth place") }, singleLine = true)
            DateField("Death date", deathDate) { deathDate = it }
            OutlinedTextField(deathPlace, { deathPlace = it }, Modifier.fillMaxWidth(), label = { Text("Death place") }, singleLine = true)

            Text("Living status", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = living == null, onClick = { living = null }, label = { Text("Automatic") })
                FilterChip(selected = living == true, onClick = { living = true }, label = { Text("Living") })
                FilterChip(selected = living == false, onClick = { living = false }, label = { Text("Deceased") })
            }

            OutlinedTextField(
                phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone number") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            spouseFams.forEach { f ->
                val other = f.otherSpouse(id)?.let { s.tree.person(it)?.fullName } ?: "unknown spouse"
                Text("Marriage to $other", style = MaterialTheme.typography.titleSmall)
                DateField("Marriage date", marriage[f.id].orEmpty()) { marriage[f.id] = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = divorced[f.id] == true, onCheckedChange = { divorced[f.id] = it })
                    Text("Divorced (hides the anniversary)")
                }
            }

            Text("My events", style = MaterialTheme.typography.titleSmall)
            Text("Add yearly dates of your own — a graduation, a move, the day you met.", style = MaterialTheme.typography.bodySmall)
            customs.forEach { row ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(row.label, { row.label = it }, Modifier.fillMaxWidth(), label = { Text("What") }, singleLine = true)
                        DateField("When", row.date) { row.date = it }
                    }
                    IconButton(onClick = { customs.remove(row) }) { Icon(Icons.Default.Delete, "Remove") }
                }
            }
            OutlinedButton(onClick = { customs.add(CustomRow(UUID.randomUUID().toString(), "", "")) }) {
                Icon(Icons.Default.Add, null); Text(" Add event")
            }

            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().height(140.dp), label = { Text("Notes") })

            Spacer(Modifier.height(8.dp))
            Button(onClick = { save() }, Modifier.fillMaxWidth()) { Text("Save changes") }
            if (p.edited) TextButton(onClick = { confirmRevert = true }, Modifier.fillMaxWidth()) {
                Text("Revert to original file", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRevert) AlertDialog(
        onDismissRequest = { confirmRevert = false },
        title = { Text("Revert ${p.givenName}?") },
        text = { Text("This removes all your edits and added events for this person.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRevert = false
                vm.saveEdits(id, null, spouseFams.associate { it.id to null }, emptyList())
                vm.pop()
            }) { Text("Revert") }
        },
        dismissButton = { TextButton(onClick = { confirmRevert = false }) { Text("Cancel") } }
    )
}

@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val parsed = GedDate.parse(value)
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = value.isNotBlank() && parsed?.year == null && parsed?.month == null,
        supportingText = {
            Text(
                when {
                    value.isBlank() -> "e.g. 12 MAR 1950, 1950-03-12, ABT 1890"
                    parsed == null || (parsed.year == null && parsed.month == null) -> "Date not recognized"
                    else -> "Reads as: ${parsed.display()}" + if (!parsed.hasMonthDay) " (no yearly reminder without day & month)" else ""
                }
            )
        }
    )
}
