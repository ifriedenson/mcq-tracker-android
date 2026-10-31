package com.mcqtracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcqtracker.android.AppViewModel
import com.mcqtracker.android.QuestionFilter
import com.mcqtracker.android.Screen
import com.mcqtracker.android.UiState
import com.mcqtracker.android.domain.Stats
import java.time.LocalDate

@Composable
fun MCQApp(viewModel: AppViewModel, onPickFolder: () -> Unit) {
    val state = viewModel.state
    if (state.overlay) {
        when (state.screen) {
            Screen.PRACTICE -> PracticeScreen(viewModel)
            Screen.SUMMARY -> SummaryScreen(viewModel)
            Screen.CARD_PRACTICE -> CardPracticeScreen(viewModel)
            else -> TodayScreen(viewModel)
        }
        return
    }
    Scaffold(
        bottomBar = { BottomBar(state.screen, viewModel::goTo) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            state.message?.let { message ->
                MessageBanner(message, viewModel::clearMessage)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when (state.screen) {
                    Screen.CARDS -> CardsScreen(viewModel)
                    Screen.QUESTIONS -> QuestionsScreen(viewModel)
                    Screen.PROGRESS -> ProgressScreen(viewModel)
                    Screen.SETTINGS -> SettingsScreen(viewModel, onPickFolder)
                    else -> TodayScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Dismiss",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(6.dp),
        )
    }
}

@Composable
private fun BottomBar(current: Screen, onNavigate: (Screen) -> Unit) {
    val tabs = listOf(
        Screen.TODAY to "Today",
        Screen.CARDS to "Cards",
        Screen.QUESTIONS to "Questions",
        Screen.PROGRESS to "Progress",
        Screen.SETTINGS to "Settings",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { (screen, label) ->
            val active = current == screen
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .fillMaxWidth(if (active) 1f else 0f)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
fun TodayScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val today = LocalDate.now()
    val overview = Stats.overview(state.questions, today)
    val dueTotal = overview.due + overview.new
    val preview = state.questions.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(text = "MCQ Tracker", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(text = "Unofficial companion", tint = MaterialTheme.colorScheme.secondary)
            Pill(text = state.vaultLabel, tint = MaterialTheme.colorScheme.primary)
        }
        if (state.sampleMode) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "You are practising the bundled sample vault. Choose your own Obsidian vault folder in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (state.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.height(28.dp))
            }
        }

        AppCard(container = MaterialTheme.colorScheme.primary) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (dueTotal == 0) "Nothing due" else "$dueTotal to review",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "${overview.due} due · ${overview.new} new",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { viewModel.startTodayQueue() },
                enabled = dueTotal > 0,
            ) {
                Text(if (dueTotal > 0) "Start practice" else "Nothing to practise")
            }
        }

        Spacer(Modifier.height(14.dp))
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatTile("Streak", "${overview.streak}d", Modifier.weight(1f))
                StatTile("Accuracy", formatPercent(overview.accuracy), Modifier.weight(1f))
                StatTile("Attempts", overview.attempts.toString(), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { viewModel.startAllQuestions() },
                modifier = Modifier.weight(1f),
                enabled = state.questions.isNotEmpty(),
            ) {
                Text("Practice all")
            }
            OutlinedButton(
                onClick = { viewModel.startMissed() },
                modifier = Modifier.weight(1f),
                enabled = overview.missed > 0,
            ) {
                Text("Missed (${overview.missed})")
            }
        }

        val priorityCount = Stats.priorityQueue(state.questions, today).size
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { viewModel.startPriorityQueue() },
            modifier = Modifier.fillMaxWidth(),
            enabled = priorityCount > 0,
        ) {
            Text("Priority review ($priorityCount)")
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader(
            title = "Question bank",
            action = "Browse all",
            onAction = { viewModel.goTo(Screen.QUESTIONS) },
        )
        Spacer(Modifier.height(8.dp))
        if (state.questions.isEmpty() && !state.loading) {
            EmptyState(
                title = "No questions yet",
                body = "Choose an Obsidian vault folder that contains MCQ Tracker notes, or keep using the samples.",
            )
        } else {
            preview.forEach { question ->
                QuestionRow(
                    question = question,
                    onClick = { viewModel.practiseOne(question.key) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (overview.readOnlyCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${overview.readOnlyCount} note(s) use FSRS or SM-2 scheduling. They are read-only: progress is kept in the app and never written back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun QuestionRow(
    question: com.mcqtracker.android.data.Question,
    onClick: () -> Unit,
    onFlag: (() -> Unit)? = null,
) {
    val progress = question.progress
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question.topic,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = question.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${question.questionId} · ${question.confidenceLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(question)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (progress.attempts == 0) "New" else formatPercent(progress.accuracy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onFlag != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (progress.flagged) "★ Flagged" else "☆ Flag",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (progress.flagged) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onFlag)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (question.schedulerManaged) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "FSRS/SM-2 · read-only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun StatusPill(question: com.mcqtracker.android.data.Question) {
    val today = LocalDate.now()
    when {
        Stats.isLeech(question) -> Pill("Leech", MaterialTheme.colorScheme.error)
        Stats.isMissed(question) -> Pill("Missed", MaterialTheme.colorScheme.error)
        Stats.isDue(question, today) && !Stats.isNew(question) -> Pill("Due", MaterialTheme.colorScheme.primary)
        Stats.isNew(question) -> Pill("New", MaterialTheme.colorScheme.tertiary)
        else -> Pill("Scheduled ${formatDay(question.progress.nextReview)}", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun FilterRow(current: QuestionFilter, onSelect: (QuestionFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuestionFilter.entries.forEach { filter ->
            val active = filter == current
            if (active) {
                Button(onClick = { onSelect(filter) }) { Text(filter.label) }
            } else {
                OutlinedButton(onClick = { onSelect(filter) }) { Text(filter.label) }
            }
        }
    }
}

@Composable
fun CenteredNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
}
