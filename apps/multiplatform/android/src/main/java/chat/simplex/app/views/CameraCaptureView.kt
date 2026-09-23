package chat.simplex.app.views

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CameraCaptureView(
  onImageCaptured: (File) -> Unit,
  onError: (ImageCaptureException) -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
  val imageCapture = remember { ImageCapture.Builder().build() }

  Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        val previewView = PreviewView(ctx)
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
            cameraProvider.bindToLifecycle(
              lifecycleOwner,
              cameraSelector,
              preview,
              imageCapture
            )
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
      },
      update = { previewView ->
        // Перепривязка при переключении фронтальной/задней камеры
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
          it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.Builder()
          .requireLensFacing(lensFacing)
          .build()

        try {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
          )
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
    )

    // Кнопка закрытия (сверху слева)
    IconButton(
      onClick = onClose,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = 48.dp, start = 16.dp)
        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
    ) {
      Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
    }

    // Нижняя панель управления (спуск и переключение сенсора)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.size(48.dp))

      // Большая кнопка затвора
      IconButton(
        onClick = {
          takePhoto(context, imageCapture, onImageCaptured, onError)
        },
        modifier = Modifier
          .size(76.dp)
          .background(Color.White, CircleShape)
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .background(Color.White, CircleShape)
        )
      }

      // Переворот камеры (селфи / основная)
      IconButton(
        onClick = {
          lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
          } else {
            CameraSelector.LENS_FACING_BACK
          }
        },
        modifier = Modifier
          .size(48.dp)
          .background(Color.Black.copy(alpha = 0.5f), CircleShape)
      ) {
        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
      }
    }
  }
}

private fun takePhoto(
  context: Context,
  imageCapture: ImageCapture,
  onImageCaptured: (File) -> Unit,
  onError: (ImageCaptureException) -> Unit
) {
  val photoFile = File(
    context.cacheDir,
    "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
  )

  val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

  imageCapture.takePicture(
    outputOptions,
    ContextCompat.getMainExecutor(context),
    object : ImageCapture.OnImageSavedCallback {
      override fun onError(exc: ImageCaptureException) {
        onError(exc)
      }

      override fun onImageSaved(output: ImageCapture.OutputFileResults) {
        onImageCaptured(photoFile)
      }
    }
  )
}
