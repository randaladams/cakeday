package com.randaladams.cakeday.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.randaladams.cakeday.data.TreeState

@Composable
fun AppRoot(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = vm.backStack.size > 1) { vm.pop() }

    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    when (val screen = vm.current) {
        Screen.Main -> MainScreen(vm, s, snackbar)
        is Screen.PersonDetail -> PersonScreen(vm, s, screen.id, snackbar)
        is Screen.EditPerson -> EditPersonScreen(vm, s, screen.id)
        Screen.Settings -> SettingsScreen(vm, s, snackbar)
        Screen.PickHomePerson -> PickHomeScreen(vm, s)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel, s: TreeState, snackbar: SnackbarHostState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cakeday")
                        s.homePerson?.let {
                            Text("Home: ${it.fullName}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.push(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            if (s.hasTree) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = vm.tab == t,
                        onClick = { vm.tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.DAY -> Icons.Default.Star
                                    Tab.WEEK -> Icons.Default.DateRange
                                    Tab.MONTH -> Icons.Default.DateRange
                                    Tab.SEARCH -> Icons.Default.Search
                                }, contentDescription = null
                            )
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!s.hasTree) {
                EmptyTree(vm)
            } else when (vm.tab) {
                Tab.DAY -> DayView(vm, s)
                Tab.WEEK -> WeekView(vm, s)
                Tab.MONTH -> MonthView(vm, s)
                Tab.SEARCH -> SearchView(vm, s, onPick = { vm.openPerson(it) })
            }
        }
    }
}

@Composable
private fun EmptyTree(vm: MainViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importGed(it) }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Home, contentDescription = null)
        Spacer(Modifier.height(12.dp))
        Text("Welcome to Cakeday", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import a GEDCOM (.ged) file exported from Ancestry, FamilySearch, MyHeritage, " +
                "RootsMagic or similar. Cakeday keeps its own copy — your original file is never changed.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (vm.importing) CircularProgressIndicator()
        else Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .ged file") }
    }
}
