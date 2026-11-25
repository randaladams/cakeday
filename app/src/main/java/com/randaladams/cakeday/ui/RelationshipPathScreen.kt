package com.randaladams.cakeday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.randaladams.cakeday.data.TreeState
import com.randaladams.cakeday.logic.RelationshipCalculator.LinkType

/** Shows the chain of people from the home person to [targetId], joined by labelled lines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationshipPathScreen(vm: MainViewModel, s: TreeState, targetId: String) {
    val steps = remember(s, targetId) { s.relationships.path(targetId) }
    val target = s.tree.person(targetId)
    val home = s.homePerson
    val rel = s.relationships.relationship(targetId)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("How you're related") },
            navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (steps == null || target == null || home == null) {
                Text("No connection found between these people.", textAlign = TextAlign.Center)
                return@Column
            }
            if (rel != null) {
                Text(
                    "${target.fullName} is the ${rel.label.lowercase()} of ${home.fullName}",
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
            }

            val ids = listOf(home.id) + steps.map { it.toId }
            ids.forEachIndexed { i, id ->
                // The turning point where the chain stops climbing and starts coming back down
                val isCommonAncestor = i in 1 until ids.lastIndex &&
                    steps[i - 1].type == LinkType.PARENT && steps[i].type == LinkType.CHILD
                val tag = when {
                    i == 0 -> "Home person"
                    i == ids.lastIndex -> rel?.label
                    isCommonAncestor -> "Common ancestor"
                    else -> s.relationships.relationship(id)?.label
                }
                PersonNode(s, id, tag, highlight = i == 0 || i == ids.lastIndex, ancestor = isCommonAncestor) {
                    vm.openPerson(id)
                }
                if (i < steps.size) Connector("is the ${steps[i].link} of")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Tap a name to open that person.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PersonNode(s: TreeState, id: String, tag: String?, highlight: Boolean, ancestor: Boolean, onClick: () -> Unit) {
    val p = s.tree.person(id) ?: return
    val living = s.living.isLiving(id)
    val border = when {
        highlight -> MaterialTheme.colorScheme.primary
        ancestor -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bg = when {
        highlight -> MaterialTheme.colorScheme.primaryContainer
        ancestor -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (tag != null) Text(tag.uppercase(), style = MaterialTheme.typography.labelSmall, color = border)
        Text(p.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        val sub = listOf(p.lifespan(), if (living) "Living" else "").filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
    }
}

/** A vertical line between two people with the link ("is the son of") in a pill on top of it. */
@Composable
private fun Connector(label: String) {
    val lineColor = MaterialTheme.colorScheme.outline
    Box(Modifier.height(56.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(lineColor))
        Text(
            "$label  ↓",
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, lineColor, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
