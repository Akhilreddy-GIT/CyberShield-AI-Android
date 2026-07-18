package com.cybershield.ai.presentation.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.LoadingBox
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerLowest
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary
import com.cybershield.ai.data.remote.dto.TimelineEventDto

@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar(showBack = true, onBack = onBack)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        "Incident Timeline",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "EVENT TIMELINE",
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("What happened?", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald,
                            unfocusedBorderColor = Mist,
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            cursorColor = Emerald,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.eventTime,
                        onValueChange = viewModel::onEventTimeChange,
                        label = { Text("When? (optional, approximate OK)", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald,
                            unfocusedBorderColor = Mist,
                            focusedContainerColor = CardWhite,
                            unfocusedContainerColor = CardWhite,
                            cursorColor = Emerald,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PrimaryActionButton(
                        text = if (state.isSaving) "Saving…" else "Add event",
                        onClick = viewModel::add,
                        enabled = !state.isSaving && state.description.isNotBlank(),
                    )
                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ErrorBanner(state.error!!)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    when {
                        state.isLoading -> LoadingBox()
                        state.events.isEmpty() -> EmptyState("No timeline events yet.")
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            modifier = Modifier.padding(bottom = 100.dp),
                        ) {
                            itemsIndexed(state.events) { index, e ->
                                TimelineEventRow(
                                    event = e,
                                    isLast = index == state.events.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Vertical connected-dot timeline row matching the "EVENT TIMELINE"
 * reference screen: a marker dot connected by a line to the next event,
 * with the description/time in a hairline card beside it. */
@Composable
private fun TimelineEventRow(event: TimelineEventDto, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Dot + connecting line column
        Column(
            modifier = Modifier.width(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(12.dp)
                    .background(ObsidianSurfaceContainerLowest, CircleShape)
                    .padding(2.dp)
                    .background(Emerald, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .background(Emerald.copy(alpha = 0.25f)),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                event.created_at.take(19).replace('T', ' ').uppercase(),
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Text(event.description, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (!event.event_time.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Reported time: ${event.event_time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray,
                    )
                }
            }
        }
    }
}
