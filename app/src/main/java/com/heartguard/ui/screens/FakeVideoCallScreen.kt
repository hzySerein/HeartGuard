@file:OptIn(UnstableApi::class)

package com.heartguard.ui.screens

import com.heartguard.utils.DebugLogger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.heartguard.R
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.ui.theme.WarningRed
import com.heartguard.viewmodel.FakeVideoCallViewModel
import com.heartguard.viewmodel.VideoCallState
import kotlinx.coroutines.delay

private val VideoOverlayPanel = Color.Black.copy(alpha = 0.58f)
private val VideoPreviewBorder = Color.White.copy(alpha = 0.55f)

@Composable
fun FakeVideoCallScreen(
    fakeVideoCallViewModel: FakeVideoCallViewModel,
    scenarioId: String? = null,
    onDrillFinished: (Boolean) -> Unit = {},
) {
    val scenario = remember(scenarioId) { findFraudScenarioById(scenarioId) }
    val videoPracticeScript = remember(scenario) { scenario.toCompactPracticeScript() }
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val callState by fakeVideoCallViewModel.callState.collectAsStateWithLifecycle()
    val videoResId by fakeVideoCallViewModel.videoResId.collectAsStateWithLifecycle()
    val sessionId by fakeVideoCallViewModel.sessionId.collectAsStateWithLifecycle()
    var warningOverlayDismissed by remember(sessionId) { mutableStateOf(false) }
    var hasRequestedCameraPermission by remember { mutableStateOf(false) }
    var userInitiatedHangUp by remember(sessionId) { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasRequestedCameraPermission = true
        hasCameraPermission = granted
    }

    fun requestCameraPermission() {
        val shouldOpenSettings = hasRequestedCameraPermission &&
            activity?.let { appActivity ->
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    appActivity,
                    Manifest.permission.CAMERA,
                )
            } == true

        if (shouldOpenSettings) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        } else {
            hasRequestedCameraPermission = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(videoResId, sessionId) {
        if (videoResId == 0) {
            return@LaunchedEffect
        }
        val videoUri = Uri.parse("android.resource://${context.packageName}/$videoResId")
        player.setMediaItem(MediaItem.fromUri(videoUri))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(callState, sessionId) {
        when (callState) {
            VideoCallState.CALLING -> {
                warningOverlayDismissed = false
                delay(WARNING_DELAY_MILLIS)
                fakeVideoCallViewModel.triggerWarning(sessionId)
            }

            VideoCallState.WARNING -> {
                warningOverlayDismissed = false
            }

            VideoCallState.HUNG_UP -> {
                player.stop()
                onDrillFinished(userInitiatedHangUp)
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        MATCH_PARENT,
                        MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.14f))
        )

        VideoDrillTag(
            scenario = scenario,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 20.dp),
        )

        CameraPreviewWindow(
            hasCameraPermission = hasCameraPermission,
            onRequestCameraPermission = {
                requestCameraPermission()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 20.dp),
        )

        BottomSpeechCard(
            script = videoPracticeScript,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 124.dp),
        )

        if (callState == VideoCallState.WARNING && !warningOverlayDismissed) {
            WarningOverlay(
                keywords = scenario.keywords,
                onDismiss = {
                    warningOverlayDismissed = true
                },
            )
        }

        Button(
            onClick = {
                userInitiatedHangUp = true
                fakeVideoCallViewModel.hangUpCall()
            },
            enabled = callState != VideoCallState.HUNG_UP,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WarningRed,
                contentColor = Color.White,
                disabledContainerColor = WarningRed.copy(alpha = 0.7f),
                disabledContentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.fake_call_hang_up),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun VideoDrillTag(
    scenario: FraudScenario,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(scenario.labelRes),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(VideoOverlayPanel)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CameraPreviewWindow(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCameraUnavailable by remember(hasCameraPermission) { mutableStateOf(false) }
    val shouldShowCameraPreview = hasCameraPermission && !isCameraUnavailable

    Box(
        modifier = modifier
            .size(
                width = 132.dp,
                height = 176.dp,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = VideoPreviewBorder,
                shape = RoundedCornerShape(16.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldShowCameraPreview) {
            FrontCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onPreviewUnavailable = {
                    isCameraUnavailable = true
                },
            )
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.fake_video_local_preview),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fake_video_camera_unavailable),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestCameraPermission,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.fake_video_enable_camera),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrontCameraPreview(
    modifier: Modifier = Modifier,
    onPreviewUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }

    DisposableEffect(cameraProviderFuture) {
        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                cameraProviderFuture.addListener(
                    {
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { cameraPreview ->
                                cameraPreview.setSurfaceProvider(surfaceProvider)
                            }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                            )
                        } catch (error: Exception) {
                            DebugLogger.e(TAG, "Failed to bind front camera preview", error)
                            onPreviewUnavailable()
                        }
                    },
                    ContextCompat.getMainExecutor(viewContext),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun BottomSpeechCard(
    script: String,
    modifier: Modifier = Modifier,
) {
    val speakingText = stringResource(R.string.fake_call_speaking)
    val subtitleText = script.ifBlank {
        speakingText
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = subtitleText,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WarningOverlay(
    keywords: List<String>,
    onDismiss: () -> Unit,
) {
    val visibleKeywords = keywords.filter { it.isNotBlank() }.take(5)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.fake_video_warning_title),
                color = WarningRed,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "请重点识别本次场景中的风险关键词：",
                color = TextPrimary,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleKeywords.forEach { keyword ->
                    Text(
                        text = keyword,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(WarningRed.copy(alpha = 0.10f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        color = WarningRed,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "出现这些话术时，请先挂断并通过家人或官方渠道核实。",
                color = TextPrimary,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarningRed,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.fake_video_warning_confirm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val TAG = "FakeVideoCallScreen"
private const val WARNING_DELAY_MILLIS = 6_000L

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
