package chat.simplex.app

import android.content.Context
import android.util.Log
import java.io.File

object ByeDpiService {
  private const val TAG = "ByeDpiService"
  private var process: Process? = null

  const val LOCAL_IP = "127.0.0.1"
  const val LOCAL_PORT = 10808

  fun start(context: Context, cmdArgs: String = "--split 1+s --disorder 1") {
    if (process?.isAlive == true) {
      Log.d(TAG, "ByeDPI already running")
      return
    }

    val nativeDir = context.applicationInfo.nativeLibraryDir
    val binary = File(nativeDir, "libciadpi.so")

    if (!binary.exists()) {
      Log.e(TAG, "libciadpi.so not found in $nativeDir")
      return
    }

    binary.setExecutable(true, false)

    val command = mutableListOf(
      binary.absolutePath,
      "--ip", LOCAL_IP,
      "--port", LOCAL_PORT.toString()
    )
    command.addAll(cmdArgs.split(" ").filter { it.isNotBlank() })

    try {
      Log.d(TAG, "Starting ByeDPI: ${command.joinToString(" ")}")
      process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
      Log.i(TAG, "ByeDPI started on $LOCAL_IP:$LOCAL_PORT")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start ByeDPI", e)
    }
  }

  fun stop() {
    process?.destroy()
    process = null
    Log.i(TAG, "ByeDPI stopped")
  }

  fun isActive(): Boolean = process?.isAlive == true
}
