package chat.simplex.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

object SingBoxService {

  private const val CONFIG_URL = "https://cdn.jsdelivr.net/gh/awesome-vpn/awesome-vpn@master/sing-box.json"
  private const val LOCAL_PORT = 10808
  private var process: Process? = null

  @Volatile
  var isRunning = false
    private set

  fun toggle(context: Context) {
    if (isRunning) {
      stop()
      showToast(context, "VLESS отключен")
    } else {
      start(context)
    }
  }

  fun start(context: Context) {
    if (isRunning) return

    thread(name = "SingBoxStarter") {
      try {
        val configFile = prepareConfig(context)
        val binaryFile = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")

        if (!binaryFile.exists()) {
          showToast(context, "Ошибка: libsingbox.so не найден")
          return@thread
        }

        // Запуск процесса sing-box run -c config.json
        val pb = ProcessBuilder(
          binaryFile.absolutePath,
          "run",
          "-c",
          configFile.absolutePath
        )
        pb.redirectErrorStream(true)
        process = pb.start()

        // Проверяем поднятие сокета 127.0.0.1:10808
        var portOpen = false
        for (i in 0 until 15) {
          Thread.sleep(300)
          try {
            Socket().use { s ->
              s.connect(InetSocketAddress("127.0.0.1", LOCAL_PORT), 250)
              portOpen = true
            }
            break
          } catch (_: Exception) {}
        }

        if (portOpen) {
          isRunning = true
          showToast(context, "VLESS подключен (10808)")
        } else {
          stop()
          showToast(context, "Ошибка: порт 10808 не поднялся")
        }
      } catch (e: Exception) {
        showToast(context, "Сбой запуска: ${e.message}")
      }
    }
  }

  fun stop() {
    try {
      process?.destroy()
      process = null
    } catch (_: Exception) {}
    isRunning = false
  }

  // Скачивание и подмена inbounds под локальный SOCKS5
  private fun prepareConfig(context: Context): File {
    val configFile = File(context.filesDir, "singbox_active.json")

    try {
      val conn = URL(CONFIG_URL).openConnection() as HttpURLConnection
      conn.connectTimeout = 7000
      conn.readTimeout = 7000
      val rawJson = conn.inputStream.bufferedReader().use { it.readText() }

      val root = JSONObject(rawJson)

      // Заменяем секцию inbounds на наш локальный SOCKS5
      val socksInbound = JSONObject().apply {
        put("type", "socks")
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("listen_port", LOCAL_PORT)
      }
      root.put("inbounds", JSONArray().apply { put(socksInbound) })

      configFile.writeText(root.toString(2))
    } catch (e: Exception) {
      // Если интернет пропал, пробуем использовать ранее сохраненный файл
      if (!configFile.exists()) throw e
    }

    return configFile
  }

  private fun showToast(context: Context, msg: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }
}
