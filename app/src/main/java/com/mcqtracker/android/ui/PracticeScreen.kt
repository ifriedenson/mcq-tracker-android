package com.mcqtracker.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcqtracker.android.AppViewModel
import com.mcqtracker.android.StorageStatus
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import com.mcqtracker.android.data.Scheduler
import kotlinx.coroutines.delay

@Composable
fun PracticeScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val question = state.currentQuestion

    LaunchedEffect(state.practiceIndex, state.outcome == null) {
        if (state.outcome == null) {
            while (true) {
                delay(1000)
                viewModel.tick()
            }
        }
    }

    if (question == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState("Question unavailable", "It may have been removed from the vault.")
            Button(onClick = viewModel::closePractice) { Text("Back to Today") }
        }
        return
    }

    val total = state.practiceKeys.size.coerceAtLeast(1)
    val position = (state.practiceIndex + 1).coerceAtMost(total)
    val outcome = state.outcome

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = viewModel::closePractice) { Text("Close") }
            Text(
                text = "Question $position of $total",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "${state.elapsedSeconds}s" + (question.targetSeconds?.let { " / ${it}s" } ?: ""),
                style = MaterialTheme.typography.labelLarge,
                color = if (question.targetSeconds != null && state.elapsedSeconds > question.targetSeconds) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        LinearProgressIndicator(
            progress = { position.toFloat() / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(question.topic, MaterialTheme.colorScheme.secondary)
                Pill(question.confidenceLabel, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))

            if (question.stimulus.isNotBlank()) {
                AppCard(container = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "Stimulus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = question.stimulus, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(text = question.prompt, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (question.hint.isNotBlank()) {
                TextButton(onClick = viewModel::toggleHint) {
                    Text(if (state.showHint) "Hide hint" else "Show hint")
                }
                if (state.showHint) {
                    AppCard(container = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(text = question.hint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (question.mode) {
                QuestionMode.SELF_GRADED -> {
                    AppCard {
                        Text(
                            text = "This is a self-graded question.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (question.answerKey.isNotBlank()) "Expected answer: ${question.answerKey}" else "Answer it in your head, then grade yourself.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    question.choices.forEach { choice ->
                        val selected = state.selected.contains(choice.letter)
                        ChoiceCard(
                            letter = choice.letter,
                            text = choice.text,
                            selected = selected,
                            enabled = outcome == null,
                            onClick = { viewModel.toggleChoice(choice.letter) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "How confident are you?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Guessing", 2 to "Unsure", 3 to "Confident").forEach { (value, label) ->
                    val active = state.confidence == value
                    if (active) {
                        Button(onClick = { viewModel.setConfidence(value) }, enabled = outcome == null) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setConfidence(value) }, enabled = outcome == null) {
                            Text(label)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (outcome == null) {
                if (question.mode == QuestionMode.SELF_GRADED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.submit(selfGrade = false) },
                            modifier = Modifier.weight(1f),
                        ) { Text("I got it wrong") }
                        Button(
                            onClick = { viewModel.submit(selfGrade = true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("I got it right") }
                    }
                } else {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = viewModel.canSubmit(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Submit answer") }
                }
            } else {
                ResultCard(
                    correct = outcome.correct,
                    correctAnswer = outcome.correctAnswer,
                    explanation = outcome.explanation,
                    intervalDays = outcome.intervalDays,
                    nextReview = outcome.nextReview.toString(),
                    storage = outcome.storage,
                    errorType = outcome.errorType,
                    canTag = !outcome.correct,
                    onTag = viewModel::tagError,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = viewModel::nextQuestion, modifier = Modifier.fillMaxWidth()) {
                    Text(if (position >= total) "See summary" else "Next question")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ChoiceCard(
    letter: String,
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultCard(
    correct: Boolean,
    correctAnswer: String,
    explanation: String,
    intervalDays: Int,
    nextReview: String,
    storage: StorageStatus,
    errorType: String,
    canTag: Boolean,
    onTag: (String) -> Unit,
) {
    val tint = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    AppCard(container = tint.copy(alpha = 0.10f)) {
        Text(
            text = if (correct) "Correct" else "Not quite",
            style = MaterialTheme.typography.titleLarge,
            color = tint,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = "Answer: $correctAnswer", style = MaterialTheme.typography.bodyLarge)
        if (explanation.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = explanation, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = tint.copy(alpha = 0.3f))
        Spacer(Modifier.height(10.dp))
        KeyValueRow("Next review", "$nextReview (in $intervalDays day${if (intervalDays == 1) "" else "s"})")
        KeyValueRow("Storage", storage.label)
        if (errorType.isNotBlank()) {
            KeyValueRow("Error tag", errorType)
        }
        if (canTag) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "What went wrong?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Scheduler.ERROR_TAGS.chunked(2).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { tag ->
                            OutlinedButton(onClick = { onTag(tag) }) { Text(tag) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val summary = state.summary
    if (summary == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState("No summary yet", "Finish a practice session to see results.")
            Button(onClick = viewModel::closePractice) { Text("Back to Today") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(text = "Session summary", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                    CircularProgressIndicator(
                        progress = { summary.accuracy.toFloat() },
                        modifier = Modifier.size(92.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = formatPercent(summary.accuracy),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Spacer(Modifier.padding(horizontal = 8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    KeyValueRow("Answered", summary.answered.toString())
                    KeyValueRow("Correct", summary.correct.toString())
                    KeyValueRow("Total time", formatDuration(summary.totalSeconds))
                    KeyValueRow("Average", formatDuration(summary.averageSeconds))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "Missed questions")
        Spacer(Modifier.height(8.dp))
        if (summary.missed.isEmpty()) {
            CenteredNote("Nothing missed in this session.")
        } else {
            summary.missed.forEach { record ->
                AppCard(onClick = { viewModel.practiseOne(record.key) }) {
                    Text(text = record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You chose: ${record.chosen.ifBlank { "self-graded" }} · ${formatDuration(record.seconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = viewModel::closePractice, modifier = Modifier.weight(1f)) {
                Text("Back to Today")
            }
            Button(
                onClick = { viewModel.startPractice(summary.missed.map { it.key }) },
                modifier = Modifier.weight(1f),
                enabled = summary.missed.isNotEmpty(),
            ) { Text("Review misses") }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StorageStatusBadge(status: StorageStatus) {
    val tint: Color = when (status) {
        StorageStatus.SAVED_TO_NOTE -> MaterialTheme.colorScheme.tertiary
        StorageStatus.APP_ONLY -> MaterialTheme.colorScheme.secondary
        StorageStatus.NOTE_CHANGED -> MaterialTheme.colorScheme.error
    }
    Text(
        text = status.label,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        textAlign = TextAlign.Start,
    )
}
