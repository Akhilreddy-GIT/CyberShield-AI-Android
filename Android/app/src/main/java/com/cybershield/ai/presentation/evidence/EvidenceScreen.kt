package com.cybershield.ai.presentation.evidence

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.cybershield.ai.BuildConfig
import com.cybershield.ai.data.remote.dto.EvidenceItemDto
import com.cybershield.ai.presentation.components.CyberShieldTopBar
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.EvidenceTimelineCard
import com.cybershield.ai.presentation.components.GradientBackground
import com.cybershield.ai.presentation.components.PrimaryActionButton
import com.cybershield.ai.presentation.components.SecondaryActionButton
import com.cybershield.ai.presentation.components.ShimmerBox
import com.cybershield.ai.presentation.components.SkeletonCard
import com.cybershield.ai.presentation.theme.CardWhite
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.JetBrainsMono
import com.cybershield.ai.presentation.theme.Mist
import com.cybershield.ai.presentation.theme.Plum
import com.cybershield.ai.presentation.theme.SoftGray
import com.cybershield.ai.presentation.theme.TextMuted
import com.cybershield.ai.presentation.theme.TextPrimary
import java.io.File
import java.io.FileOutputStream

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Returns the absolute URL for an evidence file.
 *
 * Priority:
 *  1. Backend-provided [fileUrl] (relative path like "/api/evidence/file/{caseId}/{id}").
 *  2. Fallback: reconstruct from [caseId] + [evidenceId] — covers cache-only items
 *     served by older server versions that pre-date the file_url field.
 *
 * The base URL is taken from [BuildConfig.API_BASE_URL] so there are no
 * hardcoded hostnames anywhere.
 */
private fun buildAbsoluteFileUrl(
    fileUrl: String?,
    caseId: String,
    evidenceId: String,
): String {
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    val relative = if (!fileUrl.isNullOrBlank()) fileUrl else "/api/evidence/file/$caseId/$evidenceId"
    return "$base$relative"
}

/** True for image MIME types and common image extensions. */
private fun EvidenceItemDto.isImageFile(): Boolean =
    (file_type ?: "").contains("image", ignoreCase = true) ||
        filename.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") }

/** True for PDF MIME type or extension. */
private fun EvidenceItemDto.isPdf(): Boolean =
    (file_type ?: "").contains("pdf", ignoreCase = true) ||
        filename.lowercase().endsWith(".pdf")

// ---------------------------------------------------------------------------
// Image viewer dialog
// ---------------------------------------------------------------------------

/**
 * Full-screen AlertDialog that loads the image at [url] via Coil.
 * Shows a spinner while loading and an error state on failure.
 * Dismissed by pressing the close button or tapping outside.
 */
@Composable
private fun ImageViewerDialog(url: String, filename: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Text(
                text = filename,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
            )
        },
        text = {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = filename,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .background(Mist, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Plum, strokeWidth = 2.dp)
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .background(Mist, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.BrokenImage,
                                    contentDescription = null,
                                    tint = SoftGray,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Unable to load image.\nCheck your connection and try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        },
        containerColor = CardWhite,
        shape = RoundedCornerShape(16.dp),
    )
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun EvidenceScreen(
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    showTopBack: Boolean = true,
    viewModel: EvidenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<File?>(null) }

    // The item whose image is currently shown in the viewer dialog (null = no dialog).
    var viewingItem by remember { mutableStateOf<EvidenceItemDto?>(null) }

    // Show image dialog when viewingItem is set.
    viewingItem?.let { item ->
        val url = buildAbsoluteFileUrl(item.file_url, state.caseId, item.id)
        ImageViewerDialog(url = url, filename = item.filename, onDismiss = { viewingItem = null })
    }

    // -----------------------------------------------------------------------
    // Helpers (scoped to the composable so they can capture `context`)
    // -----------------------------------------------------------------------

    fun copyUriToCache(uri: Uri): File? {
        return try {
            val name = "upload_${System.currentTimeMillis()}"
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val mime = context.contentResolver.getType(uri)
            val ext = when {
                mime?.contains("png") == true -> ".png"
                mime?.contains("jpeg") == true || mime?.contains("jpg") == true -> ".jpg"
                mime?.contains("pdf") == true -> ".pdf"
                else -> mime?.substringAfter('/')?.let { ".$it" } ?: ".bin"
            }
            val outFile = File(context.cacheDir, "$name$ext")
            input.use { inp -> FileOutputStream(outFile).use { out -> inp.copyTo(out) } }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens a non-image evidence file via Android's ACTION_VIEW intent.
     * The file is served directly from the backend with the correct Content-Type,
     * so no local download or FileProvider is needed.
     *
     * Error handling:
     *  - Null / blank URL: shows a toast.
     *  - No app to handle the intent: shows a toast (ActivityNotFoundException caught).
     */
    fun openWithIntent(item: EvidenceItemDto) {
        val relUrl = item.file_url
        if (relUrl.isNullOrBlank()) {
            Toast.makeText(context, "File URL is not available.", Toast.LENGTH_SHORT).show()
            return
        }
        val absoluteUrl = buildAbsoluteFileUrl(relUrl, state.caseId, item.id)
        val mimeType = when {
            item.isPdf() -> "application/pdf"
            !(item.file_type.isNullOrBlank()) -> item.file_type
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl)).apply {
            setDataAndType(Uri.parse(absoluteUrl), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open ${item.filename}"))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this file type.", Toast.LENGTH_SHORT).show()
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = copyUriToCache(uri) ?: return@rememberLauncherForActivityResult
        previewFile = file
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        viewModel.upload(file, mime, description)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        GradientBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item {
                    CyberShieldTopBar(showBack = showTopBack, onBack = onBack)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Evidence Repository",
                            style = MaterialTheme.typography.displayMedium,
                            textAlign = TextAlign.Center,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "A secure, chronological record of uploaded artifacts and AI findings. Your data is analyzed privately to build a clear timeline of events.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "CASE ${state.caseId}",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optional)", color = TextMuted) },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryActionButton(
                                text = if (state.isUploading) "Uploading\u2026" else "Upload",
                                icon = Icons.Default.UploadFile,
                                onClick = { pickFile.launch("*/*") },
                                enabled = !state.isUploading,
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryActionButton(
                                text = "Camera",
                                icon = Icons.Default.CameraAlt,
                                onClick = { onOpenCamera(state.caseId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        previewFile?.let { file ->
                            val isImage = file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
                            if (isImage) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Selected evidence preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        if (state.info != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.info!!, color = MaterialTheme.colorScheme.tertiary)
                        }
                        if (state.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            ErrorBanner(state.error!!, onRetry = viewModel::refresh)
                        }
                    }
                }

                when {
                    state.isLoading && state.items.isEmpty() -> {
                        items(3) {
                            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.35f))
                                Spacer(modifier = Modifier.height(8.dp))
                                SkeletonCard()
                            }
                        }
                    }
                    state.items.isEmpty() && !state.isLoading -> {
                        item {
                            EmptyState(
                                message = "No evidence yet. Upload screenshots, PDFs, audio, or video.",
                                actionLabel = "Upload file",
                                onAction = { pickFile.launch("*/*") },
                            )
                        }
                    }
                    else -> {
                        itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 10 },
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    Text(
                                        item.uploaded_at.take(19).replace('T', ' ').uppercase(),
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isImage = item.isImageFile()

                                    // Build the absolute URL for this item.
                                    // Prefer the server-supplied file_url; fall back to
                                    // reconstructing from caseId + evidenceId for cached items.
                                    val absoluteUrl = buildAbsoluteFileUrl(
                                        fileUrl = item.file_url,
                                        caseId = state.caseId,
                                        evidenceId = item.id,
                                    )

                                    EvidenceTimelineCard(
                                        title = if (!item.extracted_text.isNullOrBlank()) {
                                            "AI Finding: Content Extracted"
                                        } else {
                                            "Evidence Logged"
                                        },
                                        description = item.description
                                            ?: if (!item.extracted_text.isNullOrBlank()) {
                                                "Patterns detected in uploaded artifact."
                                            } else {
                                                "${item.file_type ?: "File"} \u00b7 secured in vault"
                                            },
                                        filename = item.filename,
                                        location = "Case ${state.caseId.take(8)}",
                                        extractedText = item.extracted_text,
                                        isImage = isImage,
                                        // Pass the absolute URL so the card can show a thumbnail.
                                        imageUrl = absoluteUrl,
                                        onView = {
                                            if (isImage) {
                                                // Images: inline dialog viewer
                                                viewingItem = item
                                            } else {
                                                // PDFs and other types: system intent
                                                openWithIntent(item)
                                            }
                                        },
                                        onDelete = { viewModel.delete(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
