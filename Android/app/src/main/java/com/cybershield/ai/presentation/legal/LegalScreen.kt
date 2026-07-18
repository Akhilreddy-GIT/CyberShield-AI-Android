package com.cybershield.ai.presentation.legal

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cybershield.ai.domain.model.StaticContent
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PremiumCard
import com.cybershield.ai.presentation.theme.SoftGray

@Composable
fun LegalScreen(onBack: () -> Unit) {
    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CyberShieldTopBar(showBack = true, onBack = onBack)
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    item {
                        Text(
                            "Legal reference",
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Summaries mirrored from the backend knowledge base. Not lawyer-reviewed. Not legal advice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                        )
                    }
                    items(StaticContent.legalArticles, key = { it.id }) { article ->
                        PremiumCard(modifier = Modifier.fillMaxWidth()) {
                            Text(article.title, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(article.summary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Source: ${article.sourceNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftGray,
                            )
                        }
                    }
                }
            }
        }
    }
}
