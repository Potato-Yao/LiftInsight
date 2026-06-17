package com.potato.liftinsight.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.potato.liftinsight.R
import com.potato.liftinsight.camera.controller.CameraController
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CameraRecordingScreen(
    motionTitle: String,
    motionId: Int,
    setIndex: Int,
    onRecordingFinished: (videoName: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { CameraController() }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnRecordingFinished by rememberUpdatedState(onRecordingFinished)

    var isRecording by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(Quality.FHD) }
    var selectedFrameRate by remember { mutableStateOf(60) }
    var availableQualities by remember { mutableStateOf(listOf<QualityOption>()) }
    var availableFrameRates by remember { mutableStateOf(listOf(30, 60)) }
    var cameraReady by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showFrameRateMenu by remember { mutableStateOf(false) }
    var showLensMenu by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var currentLens by remember { mutableStateOf(CameraLens.BACK) }
    var availableLenses by remember { mutableStateOf(setOf(CameraLens.BACK)) }

    // Horizon indicator state
    var horizonAngle by remember { mutableFloatStateOf(0f) }

    // Tap-to-focus feedback state
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusAnimProgress by remember { mutableFloatStateOf(0f) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Tap-to-focus touch listener on the PreviewView
    DisposableEffect(previewView, boundCamera) {
        val touchListener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && boundCamera != null) {
                val factory = previewView.meteringPointFactory
                if (factory != null) {
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).build()
                    try {
                        boundCamera?.cameraControl?.startFocusAndMetering(action)
                    } catch (_: Exception) {
                        // focus not supported, ignore
                    }
                    focusPoint = Offset(event.x, event.y)
                    focusAnimProgress = 1f
                }
            }
            false
        }
        previewView.setOnTouchListener(touchListener)
        onDispose {
            previewView.setOnTouchListener(null)
        }
    }

    // Focus animation: decay progress to 0 over time
    LaunchedEffect(focusAnimProgress) {
        if (focusAnimProgress > 0f) {
            kotlinx.coroutines.delay(1000)
            focusAnimProgress = 0f
            focusPoint = null
        }
    }

    // Horizon sensor registration
    DisposableEffect(lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val gx = event.values[0]
                val gy = event.values[1]
                // Compute roll angle in degrees: 0 = phone upright, positive = clockwise tilt
                horizonAngle = Math.toDegrees(
                    kotlin.math.atan2(gx.toDouble(), -gy.toDouble())
                ).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (gravitySensor != null && sensorManager != null) {
            sensorManager.registerListener(
                sensorListener,
                gravitySensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Populate available lenses
                availableLenses = CameraLensHelper.availableLenses(context)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                if (!provider.hasCamera(cameraSelector)) {
                    setupError = context.getString(R.string.camera_no_back_camera)
                    return@addListener
                }

                val cameraInfo = try {
                    val bc = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector
                    )
                    bc.cameraInfo
                } catch (_: Exception) {
                    null
                }

                val supportedQualities = getSupportedQualities(cameraInfo)
                val qualityOptions = CameraSettingsDefaults.buildQualityOptions(supportedQualities)
                availableQualities = qualityOptions

                val defaultQuality = CameraSettingsDefaults.pickDefaultQuality(supportedQualities)
                selectedQuality = defaultQuality

                val fpsRanges = queryFrameRateRanges(context, currentLens)
                val frameRateOptions = CameraSettingsDefaults.buildFrameRateOptions(fpsRanges)
                availableFrameRates = frameRateOptions

                val defaultFrameRate = CameraSettingsDefaults.pickDefaultFrameRate(frameRateOptions)
                selectedFrameRate = defaultFrameRate

                provider.unbindAll()

                bindCameraWithReturn(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = cameraSelector,
                    quality = defaultQuality,
                    frameRate = defaultFrameRate,
                    previewView = previewView
                ) { cam, vc ->
                    boundCamera = cam
                    videoCapture = vc
                }

                cameraReady = true
            } catch (e: Exception) {
                android.util.Log.e("NativeCameraScreen", "Camera setup failed", e)
                setupError = context.getString(R.string.camera_setup_failed)
            }
        }, executor)

        onDispose {
            try {
                activeRecording?.stop()
            } catch (_: Exception) {
            }
            try {
                cameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    fun startRecording() {
        val vc = videoCapture ?: return
        val file = cameraController.createVideoOutputFile(context, motionId, setIndex)
        outputFile = file

        val outputOptions = FileOutputOptions.Builder(file).build()

        val newRecording = try {
            vc.output.prepareRecording(context, outputOptions)
                .start(
                    ContextCompat.getMainExecutor(context),
                    Consumer { event ->
                        when (event) {
                            is VideoRecordEvent.Finalize -> {
                                activeRecording = null
                                isRecording = false
                                val videoFile = outputFile
                                if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                                    if (videoFile != null && videoFile.exists() && videoFile.length() > 0L) {
                                        currentOnRecordingFinished(videoFile.name)
                                    } else {
                                        videoFile?.delete()
                                        currentOnBack()
                                    }
                                } else {
                                    android.util.Log.e(
                                        "NativeCameraScreen",
                                        "Recording finalize error: ${event.error}"
                                    )
                                    videoFile?.delete()
                                    setupError = context.getString(R.string.camera_recording_failed)
                                }
                            }

                            else -> { /* Start, Status, Pause, Resume */ }
                        }
                    }
                )
        } catch (e: Exception) {
            android.util.Log.e("NativeCameraScreen", "Failed to start recording", e)
            setupError = context.getString(R.string.camera_recording_failed)
            return
        }

        activeRecording = newRecording
        isRecording = true
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
        }
    }

    fun rebindCamera() {
        val provider = cameraProvider ?: return
        val lens = currentLens
        val selector = CameraLensHelper.createCameraSelector(lens, provider)
        bindCameraWithReturn(
            provider = provider,
            lifecycleOwner = lifecycleOwner,
            cameraSelector = selector,
            quality = selectedQuality,
            frameRate = selectedFrameRate,
            previewView = previewView
        ) { cam, vc ->
            boundCamera = cam
            videoCapture = vc
        }
    }

    fun onQualityChanged(newQuality: Quality) {
        selectedQuality = newQuality
        rebindCamera()
    }

    fun onFrameRateChanged(newFrameRate: Int) {
        selectedFrameRate = newFrameRate
        rebindCamera()
    }

    fun onLensChanged(newLens: CameraLens) {
        currentLens = newLens
        val provider = cameraProvider ?: return
        // Re-query frame rate options for the new camera
        val fpsRanges = queryFrameRateRanges(context, newLens)
        val frameRateOptions = CameraSettingsDefaults.buildFrameRateOptions(fpsRanges)
        availableFrameRates = frameRateOptions

        val defaultFrameRate = CameraSettingsDefaults.pickDefaultFrameRate(frameRateOptions)
        selectedFrameRate = defaultFrameRate

        val selector = CameraLensHelper.createCameraSelector(newLens, provider)
        bindCameraWithReturn(
            provider = provider,
            lifecycleOwner = lifecycleOwner,
            cameraSelector = selector,
            quality = selectedQuality,
            frameRate = selectedFrameRate,
            previewView = previewView
        ) { cam, vc ->
            boundCamera = cam
            videoCapture = vc
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Horizon indicator overlay
        if (cameraReady && setupError == null) {
            val density = LocalDensity.current
            val strokePx = with(density) { 2.dp.toPx() }
            val dotRadiusPx = with(density) { 2.5.dp.toPx() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height * 0.35f
                val lineLength = size.width * 0.55f

                val angleRad = Math.toRadians(horizonAngle.toDouble()).toFloat()
                val dx = cos(angleRad) * lineLength / 2f
                val dy = sin(angleRad) * lineLength / 2f

                // Draw horizon line
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(centerX - dx, centerY - dy),
                    end = Offset(centerX + dx, centerY + dy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )

                // Draw small center dot
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = dotRadiusPx,
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Tap-to-focus visual feedback
        if (focusPoint != null && focusAnimProgress > 0f) {
            val point = focusPoint!!
            val density = LocalDensity.current
            val ringRadius = 40.dp
            val ringRadiusPx = with(density) { ringRadius.toPx() }
            val alpha = focusAnimProgress
            val scale = 1f - focusAnimProgress * 0.3f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            (point.x - ringRadiusPx).toInt(),
                            (point.y - ringRadiusPx).toInt()
                        )
                    }
                    .size(ringRadius * 2)
            ) {
                val r = size.minDimension / 2f * (1f + (1f - scale))
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.8f),
                    radius = r,
                    style = Stroke(width = 3f)
                )
            }
        }

        if (!cameraReady && setupError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.camera_initializing),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (setupError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = setupError ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        onClick = currentOnBack,
                        shape = RoundedCornerShape(24.dp),
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
        }

        if (cameraReady && setupError == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isRecording) stopRecording()
                        currentOnBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRecording) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onError,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = stringResource(R.string.camera_recording_indicator),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }

                        // Camera switch button when multiple lenses are available
                        if (!isRecording && availableLenses.size > 1) {
                            Box {
                                IconButton(onClick = { showLensMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = stringResource(R.string.camera_switch_lens),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showLensMenu,
                                    onDismissRequest = { showLensMenu = false }
                                ) {
                                    availableLenses.forEach { lens ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(lens.labelRes),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (lens == currentLens) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showLensMenu = false
                                                onLensChanged(lens)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isRecording) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Surface(
                                    onClick = { showQualityMenu = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = CameraSettingsDefaults.labelForQuality(
                                                selectedQuality
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false }
                                ) {
                                    availableQualities.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        8.dp
                                                    )
                                                ) {
                                                    Text(
                                                        text = option.label,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (option.quality == selectedQuality) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showQualityMenu = false
                                                onQualityChanged(option.quality)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box {
                                Surface(
                                    onClick = { showFrameRateMenu = true },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${selectedFrameRate} fps",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showFrameRateMenu,
                                    onDismissRequest = { showFrameRateMenu = false }
                                ) {
                                    availableFrameRates.forEach { fps ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        8.dp
                                                    )
                                                ) {
                                                    Text(
                                                        text = "$fps fps",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (fps == selectedFrameRate) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedFrameRate = fps
                                                showFrameRateMenu = false
                                                rebindCamera()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isRecording) {
                        Text(
                            text = motionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val recordButtonColor = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary

                    Surface(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                startRecording()
                            }
                        },
                        shape = CircleShape,
                        color = recordButtonColor,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRecording) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(28.dp)
                                ) {}
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .border(
                                            3.dp,
                                            recordButtonColor,
                                            CircleShape
                                        )
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getSupportedQualities(cameraInfo: CameraInfo?): Set<Quality> {
    if (cameraInfo == null) return setOf(Quality.HD, Quality.SD)

    return try {
        val videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)
        val qualities = videoCapabilities.getSupportedQualities(DynamicRange.SDR)
        qualities.toSet()
    } catch (e: Exception) {
        android.util.Log.w("NativeCameraScreen", "Failed to query supported qualities", e)
        setOf(Quality.HD, Quality.SD)
    }
}

/**
 * Binds Preview + VideoCapture use cases to the lifecycle and returns the
 * bound [Camera] as well as the [VideoCapture] use case.
 */
private fun bindCameraWithReturn(
    provider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraSelector: CameraSelector,
    quality: Quality,
    frameRate: Int,
    previewView: PreviewView,
    onReady: (Camera, VideoCapture<Recorder>) -> Unit
) {
    provider.unbindAll()

    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val qualitySelector = QualitySelector.from(quality)
    val recorder = Recorder.Builder()
        .setQualitySelector(qualitySelector)
        .build()
    val vc = VideoCapture.Builder<Recorder>(recorder)
        .setTargetFrameRate(Range.create(frameRate, frameRate))
        .build()

    val camera = provider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        preview,
        vc
    )

    onReady(camera, vc)
}

private fun queryFrameRateRanges(context: Context, lens: CameraLens): List<Pair<Int, Int>> {
    return try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptyList()

        val targetFacing = when (lens) {
            CameraLens.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
            else -> CameraCharacteristics.LENS_FACING_BACK
        }

        // Prefer the matching facing; fall back to any camera if none found.
        var cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == targetFacing
            } catch (_: Exception) {
                false
            }
        }

        if (cameraId == null) {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        }

        if (cameraId == null) return emptyList()

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val ranges = characteristics
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?: return emptyList()

        ranges.map { range: Range<Int> ->
            Pair(range.lower, range.upper)
        }
    } catch (e: Exception) {
        android.util.Log.w("NativeCameraScreen", "Failed to query frame rate ranges", e)
        emptyList()
    }
}
