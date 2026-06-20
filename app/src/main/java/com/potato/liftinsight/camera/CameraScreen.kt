package com.potato.liftinsight.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.controller.CameraController
import java.io.File

@Composable
fun CameraScreen(
    motionTitle: String,
    motionId: Int,
    setIndex: Int,
    onRecordingFinished: (videoName: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    captureMode: CameraCaptureMode = CameraCaptureMode.Default
) {
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnRecordingFinished by rememberUpdatedState(onRecordingFinished)
    val cameraController = remember { CameraController() }

    val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) ==
                    PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { granted -> granted }
    }

    // External camera capture state
    var externalOutputUri by remember { mutableStateOf<Uri?>(null) }
    var externalOutputFile by remember { mutableStateOf<File?>(null) }

    val externalVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val outputFile = externalOutputFile
        externalOutputFile = null
        externalOutputUri = null

        if (success && outputFile != null && outputFile.exists() && outputFile.length() > 0L) {
            currentOnRecordingFinished(outputFile.name)
        } else {
            outputFile?.delete()
            currentOnRecordingFinished(null)
        }
    }

    // When External mode, immediately launch the system video capture
    if (captureMode == CameraCaptureMode.External) {
        LaunchedEffect(Unit) {
            val outputFile = cameraController.createVideoOutputFile(context, motionId, setIndex)
            externalOutputFile = outputFile

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
            } catch (e: Exception) {
                android.util.Log.e("CameraScreen", "Failed to create FileProvider URI", e)
                Toast.makeText(context, context.getString(R.string.camera_external_error_open), Toast.LENGTH_SHORT).show()
                externalOutputFile = null
                currentOnRecordingFinished(null)
                currentOnBack()
                return@LaunchedEffect
            }

            externalOutputUri = uri
            try {
                externalVideoLauncher.launch(uri)
            } catch (e: Exception) {
                android.util.Log.e("CameraScreen", "Failed to launch external camera", e)
                Toast.makeText(context, context.getString(R.string.camera_external_error_open), Toast.LENGTH_SHORT).show()
                externalOutputFile?.delete()
                externalOutputFile = null
                externalOutputUri = null
                currentOnRecordingFinished(null)
                currentOnBack()
            }
        }

        // Show a brief message while launching
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.camera_initializing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    if (!hasPermissions) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.camera_permission_required),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                onClick = {
                    permissionLauncher.launch(requiredPermissions)
                },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.camera_grant_permission),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        return
    }

    CameraRecordingScreen(
        motionTitle = motionTitle,
        motionId = motionId,
        setIndex = setIndex,
        onRecordingFinished = onRecordingFinished,
        onBack = onBack,
        modifier = modifier
    )
}
