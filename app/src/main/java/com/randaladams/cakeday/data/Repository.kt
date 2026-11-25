package com.randaladams.cakeday.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.randaladams.cakeday.gedcom.GedcomParser
import com.randaladams.cakeday.logic.EventEngine
import com.randaladams.cakeday.logic.LivingCalculator
import com.randaladams.cakeday.logic.RelationshipCalculator
import com.randaladams.cakeday.model.AppSettings
import com.randaladams.cakeday.model.CustomEvent
import com.randaladams.cakeday.model.Edits
import com.randaladams.cakeday.model.FamilyEdit
import com.randaladams.cakeday.model.FamilyTree
import com.randaladams.cakeday.model.PersonEdit
import com.randaladams.cakeday.notify.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Everything the screens need, rebuilt whenever the tree, edits or settings change. */
data class TreeState(
    val tree: FamilyTree,
    val originalTree: FamilyTree,
    val edits: Edits,
    val settings: AppSettings,
    val living: LivingCalculator,
    val relationships: RelationshipCalculator,
    val engine: EventEngine,
    val fileName: String?
) {
    val hasTree: Boolean get() = tree.people.isNotEmpty()
    val homePerson get() = tree.person(settings.homePersonId)
}

class Repository private constructor(private val context: Context) {

    /** Private copy of the imported file. The user's original .ged is only ever read. */
    private val treeFile = File(context.filesDir, "tree.ged")
    private val editStore = EditStore(File(context.filesDir, "edits.json"))
    private val settingsStore = SettingsStore(context)
    private val mutex = Mutex()

    private var original: FamilyTree? = null
    private var edits: Edits = Edits()
    private var settings: AppSettings = settingsStore.load()

    private val _state = MutableStateFlow<TreeState?>(null)
    val state: StateFlow<TreeState?> = _state.asStateFlow()

    suspend fun ensureLoaded(): TreeState = mutex.withLock {
        _state.value ?: withContext(Dispatchers.IO) {
            original = if (treeFile.exists()) runCatching { GedcomParser.parse(treeFile.inputStream()) }.getOrNull() else null
            edits = editStore.load()
            settings = settingsStore.load()
            rebuild()
        }
    }

    /** Reads the chosen .ged, validates it, and keeps a private copy. Returns the number of people. */
    suspend fun importGed(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Couldn't open the file")
            val parsed = GedcomParser.parse(GedcomParser.decode(bytes))
            require(parsed.people.isNotEmpty()) { "No people found. Is this a GEDCOM (.ged) file?" }
            mutex.withLock {
                val tmp = File(context.filesDir, "tree.ged.tmp")
                tmp.writeBytes(bytes)
                tmp.renameTo(treeFile)
                original = parsed
                settingsStore.treeFileName = displayName(uri)
                if (settings.homePersonId != null && parsed.people[settings.homePersonId] == null) {
                    settings = settings.copy(homePersonId = null)
                    settingsStore.save(settings)
                }
                rebuild()
            }
            NotificationScheduler.schedule(context, settings)
            parsed.people.size
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val s = mutex.withLock {
            settings = transform(settings)
            settingsStore.save(settings)
            withContext(Dispatchers.Default) { rebuild() }
            settings
        }
        NotificationScheduler.schedule(context, s)
    }

    /** Saves edits for one person. Pass null for [edit] to revert the person to the original file. */
    suspend fun savePersonEdits(
        personId: String,
        edit: PersonEdit?,
        familyEdits: Map<String, FamilyEdit?>,
        customEvents: List<CustomEvent>
    ) = mutex.withLock {
        val persons = edits.persons.toMutableMap()
        if (edit == null || edit.isEmpty) persons.remove(personId) else persons[personId] = edit
        val fams = edits.families.toMutableMap()
        familyEdits.forEach { (fid, fe) -> if (fe == null) fams.remove(fid) else fams[fid] = fe }
        val customs = edits.customEvents.filter { it.personId != personId } + customEvents
        edits = Edits(persons, fams, customs)
        withContext(Dispatchers.IO) { editStore.save(edits) }
        withContext(Dispatchers.Default) { rebuild() }
    }

    suspend fun clearAllEdits() = mutex.withLock {
        edits = Edits()
        withContext(Dispatchers.IO) { editStore.save(edits) }
        withContext(Dispatchers.Default) { rebuild() }
    }

    private fun rebuild(): TreeState {
        val orig = original ?: FamilyTree.EMPTY
        val tree = edits.applyTo(orig)
        val living = LivingCalculator(tree, LocalDate.now().year, settings.maxAge, settings.unknownIsLiving)
        val state = TreeState(
            tree = tree,
            originalTree = orig,
            edits = edits,
            settings = settings,
            living = living,
            relationships = RelationshipCalculator(tree, settings.homePersonId),
            engine = EventEngine(tree, living, settings.includeApproximateDates),
            fileName = settingsStore.treeFileName
        )
        _state.value = state
        return state
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
