package com.cybershield.ai.presentation.evidence

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cybershield.ai.presentation.components.EmptyState
import com.cybershield.ai.presentation.components.ErrorBanner
import com.cybershield.ai.presentation.components.LoadingBox
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceScreen(
    onBack: () -> Unit,
    onOpenCamera: (String) -> Unit,
    viewModel: EvidenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<File?>(null) }

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

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = copyUriToCache(uri) ?: return@rememberLauncherForActivityResult
        previewFile = file
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        viewModel.upload(file, mime, description)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evidence vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text("Case ${state.caseId}", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { pickFile.launch("*/*") },
                    enabled = !state.isUploading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Text(" Upload")
                }
                OutlinedButton(
                    onClick = { onOpenCamera(state.caseId) },
                    enabled = !state.isUploading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(" Camera")
                }
            }
            if (state.isUploading) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Uploading…")
            }
            previewFile?.let { file ->
                val isImage = file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
                if (isImage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = file,
                        contentDescription = "Selected evidence preview",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
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
                ErrorBanner(state.error!!)
            }
            Spacer(modifier = Modifier.height(12.dp))
            when {
                state.isLoading -> LoadingBox()
                state.items.isEmpty() -> EmptyState("No evidence yet. Upload screenshots, PDFs, audio, or video.")
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.filename, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${item.file_type ?: "-"} · ${item.uploaded_at}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    IconButton(onClick = { viewModel.delete(item.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                                if (!item.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(item.description!!)
                                }
                                if (!item.extracted_text.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("OCR text", fontWeight = FontWeight.Medium)
                                    Text(
                                        item.extracted_text!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
