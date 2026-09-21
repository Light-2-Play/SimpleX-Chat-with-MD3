package chat.simplex.app

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

object ByeDpiService {
  private const val TAG = "ByeDpiService"
  private const val PREFS_NAME = "byedpi_prefs"
  private const val KEY_PRESET_INDEX = "active_preset_index"
  private const val KEY_ENABLED = "byedpi_enabled"

  private var isRunning = false

  const val LOCAL_IP = "127.0.0.1"
  const val LOCAL_PORT = 10808

  // Проверенные пресеты против ТСПУ
  val PRESETS = listOf(
    "1. Fake + Reverse (Универсальный)" to "-s 1 -q 1 -f -1 -r 1+s -a",
    "2. Split + Offset (Без фейков)"    to "-s 2 -o 1 -q 1",
    "3. Aggressive Fake (Жесткий ТСПУ)" to "-s 1 -d 1 -f -1 -a",
    "4. Simple Disorder (Для мобильных)" to "-s 1 -d 1 -a",
    "5. DropWall-H2 (Lite)"              to "-s 3 -d -1+s -r 2+s -f -1 -n yandex.ru -t 4 -At,r,s",
    "6. AFTC (Pro)"               to "-s 4 -d -1+s -r 3+s -f -1 -n vk.ru -t 7 -At,r,s -o 1 -q 1",
  )

  init {
    try {
      System.loadLibrary("ciadpi")
      Log.i(TAG, "libciadpi.so loaded via JNI")
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to load libciadpi.so", e)
    }
  }

  private external fun startNative(args: String): Int

  private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  fun getSavedPresetIndex(context: Context): Int {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getInt(KEY_PRESET_INDEX, 0).coerceIn(0, PRESETS.size - 1)
  }

  fun isEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_ENABLED, true)
  }

  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .apply()
    if (enabled) {
      start(context)
    } else {
      stop()
      showToast(context, "ByeDPI отключен")
    }
  }

  fun start(context: Context, presetIndex: Int = getSavedPresetIndex(context)) {
    val (presetName, cmdArgs) = PRESETS[presetIndex]

    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_PRESET_INDEX, presetIndex)
      .putBoolean(KEY_ENABLED, true)
      .apply()

    thread(name = "ByeDpiStarter") {
      val fullArgs = "-i $LOCAL_IP -p $LOCAL_PORT $cmdArgs"
      val res = startNative(fullArgs)
      if (res != 0) {
        showToast(context, "Ошибка старта JNI: $res")
        return@thread
      }

      var portOpen = false
      for (i in 1..5) {
        Thread.sleep(200)
        try {
          Socket().use { socket ->
            socket.connect(InetSocketAddress(LOCAL_IP, LOCAL_PORT), 300)
            portOpen = true
          }
          break
        } catch (_: Exception) {}
      }

      if (portOpen) {
        isRunning = true
        showToast(context, "Активирован: $presetName")
      } else {
        isRunning = false
        showToast(context, "Порт не отвечает на: $presetName")
      }
    }
  }

  fun stop() {
    isRunning = false
  }

  fun isActive(): Boolean = isRunning

  // Диалог выбора конфигурации
  fun showPresetDialog(context: Context, onPresetSelected: (() -> Unit)? = null) {
    val currentIndex = getSavedPresetIndex(context)
    val names = PRESETS.map { it.first }.toTypedArray()

    AlertDialog.Builder(context)
      .setTitle("Режим обхода ByeDPI")
      .setSingleChoiceItems(names, currentIndex) { dialog, which ->
        start(context, which)
        onPresetSelected?.invoke()
        dialog.dismiss()
      }
      .setNegativeButton("Отмена", null)
      .show()
  }
}
