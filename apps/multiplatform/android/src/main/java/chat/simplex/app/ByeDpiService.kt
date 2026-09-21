package chat.simplex.app

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
  private var isRunning = false

  const val LOCAL_IP = "127.0.0.1"
  const val LOCAL_PORT = 10808

  init {
    try {
      System.loadLibrary("ciadpi")
      Log.i(TAG, "libciadpi.so successfully loaded via JNI")
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to load libciadpi.so", e)
    }
  }

  private external fun startNative(args: String): Int

  private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
  }

  // Начинаем с надежного базового сплита: -s 1
  fun start(context: Context, cmdArgs: String = "-s 1") {
    if (isRunning) return

    try {
      val fullArgs = "-i $LOCAL_IP -p $LOCAL_PORT $cmdArgs"
      val res = startNative(fullArgs)
      if (res != 0) {
        showToast(context, "JNI: ошибка вызова startNative ($res)")
        return
      }

      // Проверяем реальную доступность порта в отдельном потоке
      thread(name = "ByeDpiCheck") {
        var portOpen = false
        // Делаем 5 попыток с паузой в 200 мс
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
          showToast(context, "ByeDPI слушает $LOCAL_IP:$LOCAL_PORT (порт открыт!)")
          Log.i(TAG, "ByeDPI port 10808 is open and accepting connections")
        } else {
          isRunning = false
          showToast(context, "Ошибка: порт 10808 закрыт (ciadpi завершился с ошибкой)")
          Log.e(TAG, "ByeDPI port 10808 is not reachable")
        }
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to start native ByeDPI", e)
      showToast(context, "Ошибка старта: ${e.message}")
    }
  }

  fun stop() {
    isRunning = false
  }

  fun isActive(): Boolean = isRunning
}
