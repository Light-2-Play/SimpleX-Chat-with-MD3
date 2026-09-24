package chat.simplex.common.camera

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun NightCameraScreen(
    onPhotoCaptured: (File) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { NightCameraManager(context, lifecycleOwner) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var isNightModeEnabled by remember { mutableStateOf(true) }
    var isNightModeSupported by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    // Запуск камеры при открытии экрана
    fun updateCamera() {
        previewViewRef?.let { previewView ->
            cameraManager.startCamera(
                previewView = previewView,
                enableNightMode = isNightModeEnabled
            ) { isAvailable, isEnabled ->
                isNightModeSupported = isAvailable
                isNightModeEnabled = isEnabled
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Полноэкранный видоискатель камеры
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    previewViewRef = this
                    updateCamera()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Верхняя панель: закрыть камеру и переключатель ночного режима
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White
                )
            }

            // Кнопка ночного режима (показывается только если телефон поддерживает OEM-расширение)
            if (isNightModeSupported) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isNightModeEnabled) MaterialTheme.colors.primary.copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.5f)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isCapturing) {
                                isNightModeEnabled = !isNightModeEnabled
                                updateCamera()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Ночной режим",
                        tint = if (isNightModeEnabled) MaterialTheme.colors.onPrimary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isNightModeEnabled) "Ночь вкл" else "Ночь выкл",
                        color = if (isNightModeEnabled) MaterialTheme.colors.onPrimary else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Баннер обработки (ночная склейка длится 1-3 секунды)
        AnimatedVisibility(
            visible = isCapturing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Идет ночная обработка...\nНе двигайте устройство",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp
                )
            }
        }

        // 4. Нижняя панель с кнопкой спуска затвора
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            // Круглая кнопка затвора
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (isCapturing) Color.Gray else Color.White)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        val photoFile = File(
                            context.cacheDir,
                            "IMG_${System.currentTimeMillis()}.jpg"
                        )
                        cameraManager.takePhoto(
                            outputFile = photoFile,
                            onCaptureStart = {},
                            onPhotoTaken = { file ->
                                isCapturing = false
                                onPhotoCaptured(file)
                            },
                            onError = {
                                isCapturing = false
                            }
                        )
                    }
            )
        }
    }
}
