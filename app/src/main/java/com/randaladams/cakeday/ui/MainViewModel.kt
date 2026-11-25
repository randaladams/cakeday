package com.randaladams.cakeday.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.randaladams.cakeday.data.Repository
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.model.AppSettings
import com.randaladams.cakeday.model.CustomEvent
import com.randaladams.cakeday.model.FamilyEdit
import com.randaladams.cakeday.model.PersonEdit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Screen {
    data object Main : Screen
    data class PersonDetail(val id: String) : Screen
    data class EditPerson(val id: String) : Screen
    data object Settings : Screen
    data object PickHomePerson : Screen
}

enum class Tab(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), SEARCH("Search") }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    val state: StateFlow<TreeState?> = repo.state

    val backStack = mutableStateListOf<Screen>(Screen.Main)
    val current: Screen get() = backStack.last()

    var tab by mutableStateOf(Tab.DAY)
    var dayDate by mutableStateOf(LocalDate.now())
    var weekDate by mutableStateOf(LocalDate.now())
    var monthDate by mutableStateOf(LocalDate.now())
    var searchQuery by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var importing by mutableStateOf(false)

    init {
        viewModelScope.launch { repo.ensureLoaded() }
    }

    fun push(s: Screen) { backStack.add(s) }
    fun pop(): Boolean = if (backStack.size > 1) { backStack.removeAt(backStack.lastIndex); true } else false

    fun openPerson(id: String) = push(Screen.PersonDetail(id))

    fun importGed(uri: Uri) {
        importing = true
        viewModelScope.launch {
            repo.ensureLoaded()
            repo.importGed(uri)
                .onSuccess { message = "Imported $it people. Your original file was not changed." }
                .onFailure { message = "Import failed: ${it.message}" }
            importing = false
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun setHomePerson(id: String) {
        updateSettings { it.copy(homePersonId = id) }
        message = "Home person updated"
    }

    fun saveEdits(id: String, edit: PersonEdit?, families: Map<String, FamilyEdit?>, customs: List<CustomEvent>) {
        viewModelScope.launch {
            repo.savePersonEdits(id, edit, families, customs)
            message = if (edit == null) "Reverted to the original file" else "Changes saved"
        }
    }

    fun clearAllEdits() {
        viewModelScope.launch { repo.clearAllEdits(); message = "All edits cleared" }
    }
}
