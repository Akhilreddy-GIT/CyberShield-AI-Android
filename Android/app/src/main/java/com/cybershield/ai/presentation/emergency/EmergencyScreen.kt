package com.cybershield.ai.presentation.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybershield.ai.domain.model.StaticContent
import com.cybershield.ai.presentation.theme.CriticalRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency helplines") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "If you are in immediate danger, call 112 now.",
                    color = CriticalRed,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(StaticContent.helplines) { h ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(h.name, fontWeight = FontWeight.SemiBold)
                        Text(h.number, style = MaterialTheme.typography.titleMedium)
                        Text(h.useFor, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        val digits = h.number.filter { it.isDigit() }
                        if (digits.length >= 3 && !h.number.contains('.')) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Call")
                            }
                        } else if (h.number.contains('.')) {
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://${h.number}"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open portal")
                            }
                        }
                    }
                }
            }
        }
    }
}
