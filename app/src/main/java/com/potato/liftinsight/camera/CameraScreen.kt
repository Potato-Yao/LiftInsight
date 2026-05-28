package com.potato.liftinsight.camera

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.potato.liftinsight.ui.theme.LiftInsightMotion
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED
}

@Composable
fun CameraScreen(
    motionTitle: String,
    motionId: Int,
    setIndex: Int,
    setsInMotion: Int,
    expectedReps: Int,
    expectedWeight: Double,
    expectedIntensity: Double,
    onRecordingFinished: (videoName: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        val missingPermissions = mutableListOf<String>()
        if (!hasCameraPermission) missingPermissions.add(Manifest.permission.CAMERA)
        if (!hasAudioPermission) missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var showControls by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var resolutionText by remember { mutableStateOf("1080p") }
    var frameRateText by remember { mutableStateOf("30fps") }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var recorderRef by remember { mutableStateOf<Recorder?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var outputVideoFile by remember { mutableStateOf<File?>(null) }
    val stopCallbackRef = remember { mutableStateOf<(() -> Unit)?>(null) }

    var gravityX by remember { mutableFloatStateOf(0f) }
    var gravityY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                gravityX = -event.values[0]
                gravityY = -event.values[1]
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager?.registerListener(
            sensorListener,
            gravitySensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    val allPermissionsGranted = hasCameraPermission && hasAudioPermission

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (allPermissionsGranted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetResolution(Size(1920, 1080))
                                .build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                            val qualitySelector = QualitySelector.fromOrderedList(
                                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                            )
                            val recorder = Recorder.Builder()
                                .setQualitySelector(qualitySelector)
                                .build()
                            recorderRef = recorder

                            val videoCap = VideoCapture.Builder(recorder).build()

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                videoCap
                            )
                            camera = boundCamera
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera permission is required to record lifts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = {
                        val missingPermissions = mutableListOf<String>()
                        if (!hasCameraPermission) missingPermissions.add(Manifest.permission.CAMERA)
                        if (!hasAudioPermission) missingPermissions.add(Manifest.permission.RECORD_AUDIO)
                        if (missingPermissions.isNotEmpty()) {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Grant Permission",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && allPermissionsGranted,
            enter = fadeIn(animationSpec = tween(LiftInsightMotion.ShortDuration)),
            exit = fadeOut(animationSpec = tween(LiftInsightMotion.ShortDuration)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopControlBar(
                    resolution = resolutionText,
                    frameRate = frameRateText,
                    flashEnabled = flashEnabled,
                    onFlashToggle = {
                        flashEnabled = !flashEnabled
                        camera?.cameraControl?.enableTorch(flashEnabled)
                    },
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LevelIndicator(
                        gravityX = gravityX,
                        gravityY = gravityY,
                        modifier = Modifier.size(120.dp)
                    )
                }

                BottomControlBar(
                    recordingState = recordingState,
                    onStartRecord = {
                        val recorder = recorderRef
                        if (recorder != null) {
                            val success = startRecording(
                                context = context,
                                recorder = recorder,
                                cameraExecutor = cameraExecutor,
                                motionId = motionId,
                                setIndex = setIndex,
                                onFileReady = { file -> outputVideoFile = file },
                                onRecording = { rec -> activeRecording = rec },
                                stopCallbackRef = stopCallbackRef
                            )
                            if (success) {
                                recordingState = RecordingState.RECORDING
                            } else {
                                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onStopRecord = {
                        stopCallbackRef.value = {
                            recordingState = RecordingState.IDLE
                            onRecordingFinished(outputVideoFile?.name)
                            camera?.cameraControl?.enableTorch(false)
                        }
                        try {
                            activeRecording?.stop()
                        } catch (e: Exception) {
                            android.util.Log.e("CameraScreen", "Error stopping recording", e)
                            stopCallbackRef.value?.invoke()
                            stopCallbackRef.value = null
                        }
                    },
                    onPauseToggle = {
                        try {
                            when (recordingState) {
                                RecordingState.RECORDING -> {
                                    activeRecording?.pause()
                                    recordingState = RecordingState.PAUSED
                                }
                                RecordingState.PAUSED -> {
                                    activeRecording?.resume()
                                    recordingState = RecordingState.RECORDING
                                }
                                else -> {}
                            }
                        } catch (e: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraExecutor.shutdown()
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
private fun TopControlBar(
    resolution: String,
    frameRate: String,
    flashEnabled: Boolean,
    onFlashToggle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.4f)
            ) {
                Text(
                    text = "$resolution $frameRate",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            FilledTonalIconButton(
                onClick = onFlashToggle,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (flashEnabled) {
                        Color.White
                    } else {
                        Color.Black.copy(alpha = 0.4f)
                    },
                    contentColor = if (flashEnabled) {
                        Color.Black
                    } else {
                        Color.White
                    }
                )
            ) {
                Icon(
                    imageVector = if (flashEnabled) {
                        Icons.Rounded.FlashOn
                    } else {
                        Icons.Rounded.FlashOff
                    },
                    contentDescription = if (flashEnabled) "Turn off flash" else "Turn on flash",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LevelIndicator(
    gravityX: Float,
    gravityY: Float,
    modifier: Modifier = Modifier
) {
    val targetX by animateFloatAsState(
        targetValue = (gravityX / 9.81f).coerceIn(-1f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "levelX"
    )
    val targetY by animateFloatAsState(
        targetValue = (gravityY / 9.81f).coerceIn(-1f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "levelY"
    )

    val isHorizontal = kotlin.math.abs(gravityX) < 1.5f && kotlin.math.abs(gravityY) < 1.5f

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension / 2f - 4.dp.toPx()

        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        val crossSize = radius * 0.4f
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(centerX - crossSize, centerY),
            end = Offset(centerX + crossSize, centerY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(centerX, centerY - crossSize),
            end = Offset(centerX, centerY + crossSize),
            strokeWidth = 1.dp.toPx()
        )

        val bubbleRadius = 6.dp.toPx()
        val bubbleX = centerX + targetX * (radius - bubbleRadius - 4.dp.toPx())
        val bubbleY = centerY + targetY * (radius - bubbleRadius - 4.dp.toPx())

        if (isHorizontal) {
            drawCircle(
                color = Color(0xFF4CAF50),
                radius = bubbleRadius + 2.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }

        drawCircle(
            color = if (isHorizontal) Color(0xFF4CAF50) else Color.White,
            radius = bubbleRadius,
            center = Offset(bubbleX, bubbleY)
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.6f),
            radius = 2.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
private fun BottomControlBar(
    recordingState: RecordingState,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
            FilledTonalIconButton(
                onClick = onPauseToggle,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (recordingState == RecordingState.PAUSED) {
                        Icons.Rounded.PlayArrow
                    } else {
                        Icons.Rounded.Pause
                    },
                    contentDescription = if (recordingState == RecordingState.PAUSED) {
                        "Resume"
                    } else {
                        "Pause"
                    },
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))
        }

        FilledIconButton(
            onClick = when (recordingState) {
                RecordingState.IDLE -> onStartRecord
                else -> onStopRecord
            },
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when (recordingState) {
                    RecordingState.IDLE -> Color(0xFFE53935)
                    else -> Color.Red
                },
                contentColor = Color.White
            ),
            shape = CircleShape
        ) {
            Icon(
                imageVector = when (recordingState) {
                    RecordingState.IDLE -> Icons.Rounded.Videocam
                    else -> Icons.Rounded.Stop
                },
                contentDescription = when (recordingState) {
                    RecordingState.IDLE -> "Start recording"
                    else -> "Stop recording"
                },
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun videoFileName(motionId: Int, setIndex: Int): String {
    val instant = Instant.now()
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())
    val timestamp = formatter.format(instant)
    return "$timestamp-$motionId-$setIndex.mp4"
}

private fun startRecording(
    context: android.content.Context,
    recorder: Recorder,
    cameraExecutor: ExecutorService,
    motionId: Int,
    setIndex: Int,
    onFileReady: (File) -> Unit,
    onRecording: (Recording) -> Unit,
    stopCallbackRef: androidx.compose.runtime.MutableState<(() -> Unit)?>
): Boolean {
    val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: context.filesDir
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val videoFile = File(outputDir, videoFileName(motionId, setIndex))
    onFileReady(videoFile)

    val outputOptions = FileOutputOptions.Builder(videoFile).build()
    val pendingRecording = recorder.prepareRecording(context, outputOptions)

    var recording: Recording? = null
    recording = pendingRecording.start(cameraExecutor) { event ->
        when (event) {
            is VideoRecordEvent.Finalize -> {
                android.util.Log.d("CameraScreen", "Video recording finalized")
                try {
                    recording?.close()
                } catch (e: Exception) {
                    android.util.Log.e("CameraScreen", "Error closing recording", e)
                }
                stopCallbackRef.value?.invoke()
                stopCallbackRef.value = null
            }
            else -> {}
        }
    }

    onRecording(recording!!)
    return true
}
