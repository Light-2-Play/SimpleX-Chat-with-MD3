package chat.simplex.app

import android.util.Log
import kotlin.concurrent.thread

object ByeDpiService {
  private const val TAG = "ByeDpiService"
  private var isRunning = false

  const val LOCAL_IP = "127.0.0.1"
  const val LOCAL_PORT = 10808

  fun start(cmdArgs: String = "--split 1+s --disorder 1") {
    if (isRunning) {
      Log.d(TAG, "ByeByeDPI is already active")
      return
    }

    thread(name = "ByeByeDPI-Thread", isDaemon = true) {
      try {
        isRunning = true
        Log.i(TAG, "Starting ByeByeDPI on $LOCAL_IP:$LOCAL_PORT with args: $cmdArgs")
        val fullArgs = "--ip $LOCAL_IP --port $LOCAL_PORT $cmdArgs"
        com.romanvht.byebyedpi.ByeDpi.start(fullArgs)
      } catch (e: Throwable) {
        Log.e(TAG, "Error executing ByeByeDPI", e)
        isRunning = false
      }
    }
  }

  fun stop() {
    try {
      com.romanvht.byebyedpi.ByeDpi.stop()
      isRunning = false
      Log.i(TAG, "ByeByeDPI stopped")
    } catch (e: Throwable) {
      Log.e(TAG, "Error stopping ByeByeDPI", e)
    }
  }

  fun isActive(): Boolean = isRunning
}
