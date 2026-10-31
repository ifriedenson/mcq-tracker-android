package com.mcqtracker.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mcqtracker.android.AppViewModel
import com.mcqtracker.android.data.Flashcard
import java.time.LocalDate

@Composable
fun CardsScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val today = LocalDate.now()
    val due = state.cards.count { card ->
        val next = card.progress.nextReview
        card.progress.attempts == 0 || next == null || !next.isAfter(today)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Cards", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Flashcards from notes without question_id — $due of ${state.cards.size} ready. Front::Back, Front:::Back, a lone ? line, or ==cloze==.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (state.cards.isEmpty()) {
            EmptyState(
                title = "No cards yet",
                body = "Add a note containing a line like \"Capital of France::Paris\", or keep the bundled samples selected.",
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.cards, key = { it.key }) { card ->
                    CardRow(card) { viewModel.startCard(card.key) }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun CardRow(card: Flashcard, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = card.front,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.padding(horizontal = 6.dp))
            Pill(
                text = if (card.isNew) "New" else "${card.progress.attempts} reviews",
                tint = if (card.isNew) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun CardPracticeScreen(viewModel: AppViewModel) {
    val state = viewModel.state
    val card = state.activeCard

    if (card == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyState("Card unavailable", "It may have been removed from the vault.")
            Button(onClick = viewModel::goToCards) { Text("Back to Cards") }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = viewModel::closeCard) { Text("Close") }
            Text(text = card.kindLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (card.isNew) "New" else "${card.progress.attempts} reviews",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppCard {
            Text(
                text = "Front",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = card.front, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(14.dp))

        if (!state.cardRevealed) {
            Button(onClick = viewModel::revealCard, modifier = Modifier.fillMaxWidth()) {
                Text("Reveal answer")
            }
        } else {
            AppCard(container = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(text = card.back, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.gradeCard(false) },
                    modifier = Modifier.weight(1f),
                ) { Text("I got it wrong") }
                Button(
                    onClick = { viewModel.gradeCard(true) },
                    modifier = Modifier.weight(1f),
                ) { Text("I got it right") }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Cards use the same 1 / 3 / 7 / 21 / 60-day ladder as questions. Progress is stored in the app only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
