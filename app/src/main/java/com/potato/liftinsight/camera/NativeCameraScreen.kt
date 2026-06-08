package com.potato.liftinsight.camera

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.controller.CameraController
import java.io.File

@Composable
fun NativeCameraScreen(
    motionTitle: String,
    motionId: Int,
    setIndex: Int,
    onRecordingFinished: (videoName: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraController = remember { CameraController() }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnRecordingFinished by rememberUpdatedState(onRecordingFinished)
    var outputVideoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var launchStarted by rememberSaveable { mutableStateOf(false) }
    var resultHandled by rememberSaveable { mutableStateOf(false) }
    var systemCameraUnavailable by rememberSaveable { mutableStateOf(false) }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (resultHandled) {
            return@rememberLauncherForActivityResult
        }

        resultHandled = true
        val videoFile = outputVideoPath?.let(::File)
        val didRecord = result.resultCode == Activity.RESULT_OK &&
            videoFile != null &&
            videoFile.exists() &&
            videoFile.length() > 0L

        if (didRecord) {
            currentOnRecordingFinished(videoFile?.name)
            return@rememberLauncherForActivityResult
        }

        if (videoFile?.exists() == true) {
            videoFile.delete()
        }

        currentOnBack()
    }

    LaunchedEffect(context, motionId, setIndex, launchStarted, resultHandled) {
        if (launchStarted || resultHandled || systemCameraUnavailable) {
            return@LaunchedEffect
        }

        val videoFile = cameraController.createVideoOutputFile(
            context = context,
            motionId = motionId,
            setIndex = setIndex
        )
        val videoUri = FileProvider.getUriForFile(
            context,
            cameraController.fileProviderAuthority(context),
            videoFile
        )
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, videoFile.name, videoUri)
        }

        outputVideoPath = videoFile.absolutePath
        launchStarted = true

        try {
            captureLauncher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            android.util.Log.w("NativeCameraScreen", "System camera app not available", error)
            resultHandled = true

            if (videoFile.exists()) {
                videoFile.delete()
            }

            systemCameraUnavailable = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (systemCameraUnavailable) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.plan_camera_no_app),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        onClick = currentOnBack,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = stringResource(R.string.common_back),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = motionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.camera_opening_native),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
