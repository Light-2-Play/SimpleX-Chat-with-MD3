@file:OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)

package chat.simplex.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var outputUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraUI()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        outputUri = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)

        if (outputUri == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraUI()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCameraUI() {
        setContent {
            CameraScreen(
                onImageCaptured = {
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
                onClose = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }

    @Composable
    private fun CameraScreen(
        onImageCaptured: () -> Unit,
        onError: (ImageCaptureException) -> Unit,
        onClose: () -> Unit
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
        var isNightSightActive by remember { mutableStateOf(false) }

        var currentCamera by remember { mutableStateOf<Camera?>(null) }
        var currentImageCapture by remember { mutableStateOf<ImageCapture?>(null) }

        var minZoomRatio by remember { mutableStateOf(1.0f) }
        var maxZoomRatio by remember { mutableStateOf(1.0f) }
        var currentZoomRatio by remember { mutableStateOf(1.0f) }

        // Токены Monet
        val monetAccent = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_200))
            } else {
                Color.White
            }
        }

        val monetAccentSoft = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(ContextCompat.getColor(context, android.R.color.system_accent1_100))
            } else {
                Color.White
            }
        }

        val monetButtonBg = remember(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(ContextCompat.getColor(context, android.R.color.system_neutral1_900)).copy(alpha = 0.65f)
            } else {
                Color.Black.copy(alpha = 0.65f)
            }
        }

        fun bindCamera(previewView: PreviewView) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider)

                extensionsManagerFuture.addListener({
                    val extensionsManager = extensionsManagerFuture.get()

                    val baseSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    // Проверяем вендорное расширение Pixel
                    // 1. Проверяем наличие фирменного ночного режима вендора
                    val hasVendorNight = extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)

                    // 2. Если включен ночной режим и устройство его поддерживает — активируем нативный селектор
                    val finalSelector = if (isNightSightActive && hasVendorNight) {
                        extensionsManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
                    } else {
                        baseSelector
                    }

                    // 3. Настройка превью без Camera2Interop (ночной стек сам управляет видоискателем)
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // 4. Настройка захвата: максимальное качество для запуска многокадровой склейки
                    val imageCapture = ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    currentImageCapture = imageCapture

                    android.widget.Toast.makeText(
    this@CameraActivity,
    "Поддержка вендора: $hasVendorNight | Ночь вкл: $isNightSightActive",
    android.widget.Toast.LENGTH_LONG
).show()
                    
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, finalSelector, preview, imageCapture)
                        currentCamera = camera

                        // Сбрасываем экспозицию в 0 — фирменный ночной режим сам выставит выдержку и ISO
                        val exposureState = camera.cameraInfo.exposureState
                        if (exposureState.isExposureCompensationSupported) {
                            camera.cameraControl.setExposureCompensationIndex(0)
                        }

                        camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
                            minZoomRatio = zoomState.minZoomRatio
                            maxZoomRatio = zoomState.maxZoomRatio
                            currentZoomRatio = zoomState.zoomRatio
                       }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))
            }, ContextCompat.getMainExecutor(context))
        }

        val lensPresets = remember(minZoomRatio, maxZoomRatio, lensFacing) {
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                listOf(1.0f to "1×")
            } else {
                val list = mutableListOf<Pair<Float, String>>()
                if (minZoomRatio <= 0.75f) {
                    list.add(minZoomRatio to ".5")
                }
                list.add(1.0f to "1×")
                if (maxZoomRatio >= 2.0f) {
                    list.add(2.0f to "2×")
                }
                if (maxZoomRatio >= 5.0f) {
                    list.add(5.0f to "5×")
                }
                list
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var cachedPreviewView by remember { mutableStateOf<PreviewView?>(null) }

            // Верхняя панель (Кнопка закрытия)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 16.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(monetButtonBg, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val stroke = 2.5f.dp.toPx()
                        drawLine(monetAccentSoft, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
                        drawLine(monetAccentSoft, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
                    }
                }
            }

            // Видоискатель 4:3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray)
                    .pointerInput(currentCamera, minZoomRatio, maxZoomRatio) {
                        detectTransformGestures { _, _, zoom, _ ->
                            currentCamera?.let { cam ->
                                val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1.0f
                                val target = (current * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                                cam.cameraControl.setZoomRatio(target)
                            }
                        }
                    }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also {
                            cachedPreviewView = it
                            bindCamera(it)
                        }
                    },
                    update = {}
                )
            }

            // Нижняя панель управления
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Ряд: [Объективы] + [Кнопка Луна справа]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (lensPresets.size > 1) {
                        Row(
                            modifier = Modifier
                                .background(monetButtonBg, CircleShape)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            lensPresets.forEach { (ratio, label) ->
                                val isSelected = kotlin.math.abs(currentZoomRatio - ratio) < 0.25f

                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (isSelected) monetAccent else Color.Transparent,
                                            CircleShape
                                        )
                                        .clickable {
                                            currentCamera?.cameraControl?.setZoomRatio(ratio)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = label,
                                        style = TextStyle(
                                            color = if (isSelected) Color.Black else monetAccentSoft,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Кнопка ночного режима
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp)
                            .background(
                                if (isNightSightActive) monetAccent else monetButtonBg,
                                CircleShape
                            )
                            .clickable {
                                isNightSightActive = !isNightSightActive
                                cachedPreviewView?.let { bindCamera(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.75f, size.height * 0.15f)
                                cubicTo(
                                    size.width * 0.35f, size.height * 0.15f,
                                    size.width * 0.15f, size.height * 0.45f,
                                    size.width * 0.25f, size.height * 0.85f
                                )
                                cubicTo(
                                    size.width * 0.55f, size.height * 1.05f,
                                    size.width * 0.95f, size.height * 0.85f,
                                    size.width * 0.95f, size.height * 0.65f
                                )
                                cubicTo(
                                    size.width * 0.65f, size.height * 0.70f,
                                    size.width * 0.55f, size.height * 0.35f,
                                    size.width * 0.75f, size.height * 0.15f
                                )
                                close()
                            }
                            drawPath(
                                path = path,
                                color = if (isNightSightActive) Color.Black else monetAccentSoft
                            )
                        }
                    }
                }

                // Нижний ряд: [Спейсер] — [Затвор] — [Переворот камеры]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(48.dp))

                    // Затвор
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, monetAccent, CircleShape)
                            .padding(6.dp)
                            .background(monetAccent, CircleShape)
                            .clickable {
                                currentImageCapture?.let { capture ->
                                    takePhoto(capture, onImageCaptured, onError)
                                }
                            }
                    )

                    // Переворот камеры
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(monetButtonBg, CircleShape)
                            .clickable {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                                cachedPreviewView?.let { bindCamera(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawArc(
                                color = monetAccentSoft,
                                startAngle = 0f,
                                sweepAngle = 280f,
                                useCenter = false,
                                style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun takePhoto(
        imageCapture: ImageCapture,
        onSuccess: () -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val uri = outputUri ?: return
        val outputStream = contentResolver.openOutputStream(uri) ?: return
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    onError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    runOnUiThread { onSuccess() }
                }
            }
        )
    }
}
