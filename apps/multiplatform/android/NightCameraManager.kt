package chat.simplex.common.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors

class NightCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    fun startCamera(
        previewView: PreviewView,
        enableNightMode: Boolean = true,
        onNightModeStatus: (isAvailable: Boolean, isEnabled: Boolean) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Инициализируем менеджер расширений вендора
            val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider)

            extensionsManagerFuture.addListener({
                val extensionsManager = extensionsManagerFuture.get()
                val baseSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // 2. Проверяем, есть ли на устройстве нативный ночной режим
                val isNightAvailable = extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.NIGHT)

                // 3. Если ночной режим доступен и включен пользователем, подключаем его
                val finalSelector = if (isNightAvailable && enableNightMode) {
                    extensionsManager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.NIGHT)
                } else {
                    baseSelector
                }

                // 4. Настраиваем видоискатель
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 5. Настраиваем захват кадра на максимальное качество (для работы нейросетей)
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        finalSelector,
                        preview,
                        imageCapture
                    )
                    onNightModeStatus(isNightAvailable, isNightAvailable && enableNightMode)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))

        }, ContextCompat.getMainExecutor(context))
    }

    // Метод съёмки кадра
    fun takePhoto(
        outputFile: File,
        onCaptureStart: () -> Unit,
        onPhotoTaken: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture ?: return
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        onCaptureStart()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onPhotoTaken(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }
}
