package com.cybershield.ai.presentation.evidence
import androidx.compose.foundation.background
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onCapturedUploaded: () -> Unit,
    viewModel: EvidenceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uploadState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var captureError by remember { mutableStateOf<String?>(null) }
    var hasCaptured by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Only navigate away once the upload triggered by this screen has
    // actually finished successfully — never immediately after firing it.
    // Previously onCapturedUploaded() (which pops this screen, clearing
    // this ViewModel and cancelling its viewModelScope) was called right
    // after starting the upload, silently killing the upload coroutine
    // before the evidence ever reached the server.
    LaunchedEffect(hasCaptured, uploadState.isUploading, uploadState.info, uploadState.error) {
        if (hasCaptured && !uploadState.isUploading) {
            if (uploadState.info != null) {
                onCapturedUploaded()
            }
            // On error, stay on screen — captureError below surfaces it and
            // the user can retry the capture or go back manually.
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Capture evidence", color = com.cybershield.ai.presentation.theme.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = com.cybershield.ai.presentation.theme.Emerald,
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerLowest,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasPermission) {
                Text(
                    "Camera permission is required to capture evidence screenshots.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture,
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Button(
                    onClick = {
                        captureError = null
                        val photoFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
                        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        imageCapture.takePicture(
                            output,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    hasCaptured = true
                                    viewModel.upload(photoFile, "image/jpeg", "Camera capture")
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    captureError = exception.message ?: "Capture failed"
                                }
                            },
                        )
                    },
                    enabled = !uploadState.isUploading,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.cybershield.ai.presentation.theme.Emerald,
                        contentColor = com.cybershield.ai.presentation.theme.OnEmerald,
                        disabledContainerColor = com.cybershield.ai.presentation.theme.Emerald.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                ) {
                    Text(if (uploadState.isUploading) "Uploading…" else "Capture & upload")
                }

                if (uploadState.isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = com.cybershield.ai.presentation.theme.Emerald,
                    )
                }

                val statusText = captureError ?: uploadState.error
                statusText?.let {
                    Text(
                        it,
                        color = com.cybershield.ai.presentation.theme.CriticalRed,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(
                                com.cybershield.ai.presentation.theme.CriticalContainer.copy(alpha = 0.85f),
                                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}
