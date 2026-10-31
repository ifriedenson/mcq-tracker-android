package com.mcqtracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mcqtracker.android.AppViewModel
import com.mcqtracker.android.QuestionFilter
import com.mcqtracker.android.domain.Stats
import java.time.LocalDate

@Composable
fun QuestionsScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val today = LocalDate.now()
    val visible = state.questions.filter { question ->
        Stats.search(question, state.search) && Stats.matchesFilter(question, state.filter.name.lowercase(), today)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Questions", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            label = { Text("Search prompt, ID, skill, course…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        FilterRow(current = state.filter, onSelect = viewModel::setFilter)
        Text(
            text = "${visible.size} of ${state.questions.size} questions",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (visible.isEmpty()) {
            EmptyState("Nothing matches", "Try a different search or filter.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.key }) { question ->
                    QuestionRow(
                        question = question,
                        onClick = { viewModel.practiseOne(question.key) },
                        onFlag = { viewModel.toggleFlag(question.key) },
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun ProgressScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val today = LocalDate.now()
    val overview = Stats.overview(state.questions, today)
    val days = Stats.last7Days(state.questions, today)
    val skills = Stats.perSkill(state.questions)
    val maxCount = days.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Progress", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))

        if (overview.attempts == 0) {
            EmptyState(
                title = "No attempts yet",
                body = "Answer a few questions and your accuracy, streak and study days will appear here.",
            )
        }

        AppCard(container = MaterialTheme.colorScheme.primary) {
            Text(
                text = "Overall accuracy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatPercent(overview.accuracy),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "${overview.correct} correct of ${overview.attempts} attempts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                StatTile("Study days", overview.studyDays.toString(), Modifier.weight(1f))
                StatTile("Time", formatDuration(overview.timeSpent), Modifier.weight(1f))
                StatTile("Streak", "${overview.streak}d", Modifier.weight(1f))
            }
        }

        if (overview.leeches > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${overview.leeches} leech question(s): four or more misses in a row. Consider splitting the note or rewriting the explanation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Last 7 days")
        Spacer(Modifier.height(10.dp))
        AppCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (day.count == 0) "" else day.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height((8 + (92 * day.count / maxCount)).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (day.count == 0) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = day.date.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "Per-skill accuracy")
        Spacer(Modifier.height(8.dp))
        if (skills.isEmpty()) {
            CenteredNote("Skill stats appear once you have attempted questions that declare a skill.")
        } else {
            AppCard {
                skills.forEach { skill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = skill.skill,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatPercent(skill.accuracy)} · ${skill.attempts}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(skill.accuracy.toFloat().coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Everything here is computed from data stored on this device. The app has no network permission and sends nothing anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun SettingsScreen(viewModel: AppViewModel, onPickFolder: () -> Unit) {
    val state = viewModel.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Settings", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))

        AppCard {
            Text(text = "Vault source", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Current", state.vaultLabel)
            KeyValueRow("Mode", if (state.sampleMode) "Bundled samples" else "Your folder")
            KeyValueRow("Questions", state.questions.size.toString())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickFolder) { Text("Choose folder") }
                OutlinedButton(onClick = viewModel::rescan) { Text("Rescan") }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = viewModel::useSampleVault) { Text("Use sample vault") }
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Write progress into notes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "When on, reviewing a note updates its frontmatter: result, attempts, streak, next_review, confidence, error tag and a history row. The note body is never touched. Off by default.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.writeBackEnabled, onCheckedChange = viewModel::setWriteBack)
            }
            if (state.writeBackEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Only notes from your chosen folder are written. Notes using FSRS or SM-2 are always read-only. If a note changed in Obsidian since it was loaded, the app keeps your progress locally instead of overwriting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Read aloud", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Uses your device's text-to-speech engine to read the prompt, choices and feedback. Offline — the app still has no network permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = state.ttsEnabled, onCheckedChange = viewModel::setTtsEnabled)
            }
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(text = "Compatibility", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Question format", "MCQ Tracker frontmatter + sections")
            KeyValueRow("Scheduling", "1 / 3 / 7 / 21 / 60 day ladder")
            KeyValueRow("Read-only", "FSRS and SM-2 notes")
            KeyValueRow("Sample notes", "Bundled .qnote files")
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(text = "Privacy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No INTERNET permission, no accounts, no analytics, no network calls. Notes are read only from the folder you pick.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(text = "About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValueRow("App", "MCQ Tracker for Android 0.1.0")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Unofficial companion for the MCQ Tracker Obsidian plugin. Not affiliated with Obsidian. Licensed MIT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
