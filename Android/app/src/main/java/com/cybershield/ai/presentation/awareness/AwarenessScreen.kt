package com.cybershield.ai.presentation.awareness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybershield.ai.domain.model.StaticContent
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.theme.Plum
import com.cybershield.ai.presentation.theme.PlumContainer
import com.cybershield.ai.presentation.theme.SoftGray

@Composable
fun AwarenessScreen(onBack: () -> Unit) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar(showBack = true, onBack = onBack)
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    item {
                        Text(
                            "Cyber awareness",
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Practical tips to stay safer online.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                        )
                    }
                    itemsIndexed(StaticContent.awarenessTips) { index, tip ->
                        PremiumCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PlumContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.SemiBold, color = Plum)
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text("Tip ${index + 1}", fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = SoftGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
