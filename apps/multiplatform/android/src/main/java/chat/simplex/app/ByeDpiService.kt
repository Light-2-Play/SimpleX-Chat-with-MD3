package chat.simplex.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

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

  fun start(context: Context, cmdArgs: String = "-s 1 -d 1") {
    if (isRunning) {
      Log.d(TAG, "ByeDPI already active")
      return
    }

    try {
      val fullArgs = "-i $LOCAL_IP -p $LOCAL_PORT $cmdArgs"
      val res = startNative(fullArgs)
      if (res == 0) {
        isRunning = true
        showToast(context, "ByeDPI (JNI) запущен на $LOCAL_IP:$LOCAL_PORT")
        Log.i(TAG, "ByeDPI started on $LOCAL_IP:$LOCAL_PORT")
      } else {
        showToast(context, "Ошибка старта нативного потока (код $res)")
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to start native ByeDPI", e)
      showToast(context, "Ошибка JNI: ${e.message}")
    }
  }

  fun stop() {
    isRunning = false
    Log.i(TAG, "ByeDPI state stopped")
  }

  fun isActive(): Boolean = isRunning
}
