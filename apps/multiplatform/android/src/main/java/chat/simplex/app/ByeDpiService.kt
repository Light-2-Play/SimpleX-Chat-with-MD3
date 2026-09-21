package chat.simplex.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread

object ByeDpiService {
  private const val TAG = "ByeDpiService"
  private var process: Process? = null

  const val LOCAL_IP = "127.0.0.1"
  const val LOCAL_PORT = 10808

  private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
  }

  fun start(context: Context, cmdArgs: String = "-s 1 -d 1") {
    if (process?.isAlive == true) {
      Log.d(TAG, "ByeDPI already running")
      return
    }

    val nativeDir = context.applicationInfo.nativeLibraryDir
    val binary = File(nativeDir, "libciadpi.so")

    if (!binary.exists()) {
      val msg = "ByeDPI: libciadpi.so НЕ НАЙДЕН в $nativeDir"
      Log.e(TAG, msg)
      showToast(context, msg)
      return
    }

    binary.setExecutable(true, false)

    val command = mutableListOf(
      binary.absolutePath,
      "-i", LOCAL_IP,
      "-p", LOCAL_PORT.toString()
    )
    command.addAll(cmdArgs.split(" ").filter { it.isNotBlank() })

    thread(name = "ByeDPI-Runner", isDaemon = true) {
      try {
        Log.i(TAG, "Starting: ${command.joinToString(" ")}")
        val proc = ProcessBuilder(command)
          .redirectErrorStream(true)
          .start()
        process = proc

        showToast(context, "ByeDPI запущен на $LOCAL_IP:$LOCAL_PORT")

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line: String?
        val outputSample = StringBuilder()
        var lineCount = 0

        while (reader.readLine().also { line = it } != null) {
          Log.d("ciadpi", line ?: "")
          if (lineCount < 2) {
            outputSample.append(line).append(" ")
            lineCount++
          }
        }

        val exitCode = proc.waitFor()
        if (exitCode != 0) {
          showToast(context, "ByeDPI упал (код $exitCode): $outputSample")
        }
      } catch (e: Throwable) {
        Log.e(TAG, "ByeDPI launch error", e)
        showToast(context, "Ошибка ByeDPI: ${e.javaClass.simpleName}: ${e.message}")
      }
    }
  }

  fun stop() {
    process?.destroy()
    process = null
    Log.i(TAG, "ByeDPI stopped")
  }

  fun isActive(): Boolean = process?.isAlive == true
}
