package chat.simplex.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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
                onError = { exc ->
                    Log.e("CameraActivity", "Capture error", exc)
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
        val imageCapture = remember { ImageCapture.Builder().build() }

        // Токены палитры Monet с фоллбэком на нейтральные цвета для Android ниже 12
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
                Color(ContextCompat.getColor(context, android.R.color.system_neutral1_900)).copy(alpha = 0.6f)
            } else {
                Color.Black.copy(alpha = 0.6f)
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        } catch (e: Exception) {
                            Log.e("CameraActivity", "Camera bind error", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                        } catch (e: Exception) {
                            Log.e("CameraActivity", "Camera switch error", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Кнопка закрытия (Monet фон + крестик в тоне Accent 100)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
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

            // Нижняя панель управления
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))

                // Кнопка спуска (внешняя рамка и заливка в основном акцентном тоне Monet 200)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, monetAccent, CircleShape)
                        .padding(6.dp)
                        .background(monetAccent, CircleShape)
                        .clickable { takePhoto(imageCapture, onImageCaptured, onError) }
                )

                // Кнопка переворота камеры (Monet фон + дуга в тоне Accent 100)
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
