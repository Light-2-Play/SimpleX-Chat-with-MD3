package chat.simplex.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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

  private const val TAG = "SingBoxService"
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

        val pb = ProcessBuilder(
          binaryFile.absolutePath,
          "run",
          "-c",
          configFile.absolutePath
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        process = proc

        // 1. Вычитываем логи в фоне, чтобы буфер ОС не переполнялся и не вешал ядро
        thread(name = "SingBoxLogReader") {
          try {
            proc.inputStream.bufferedReader().useLines { lines ->
              lines.forEach { line ->
                Log.d(TAG, line)
              }
            }
          } catch (_: Exception) {}
        }

        // 2. Проверяем доступность локального сокета 10808
        var portOpen = false
        for (i in 0 until 20) {
          Thread.sleep(300)
          try {
            Socket().use { s ->
              s.connect(InetSocketAddress("127.0.0.1", LOCAL_PORT), 300)
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
          showToast(context, "Ошибка: порт 10808 не отвечает")
        }
      } catch (e: Exception) {
        showToast(context, "Сбой: ${e.message}")
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

  private fun prepareConfig(context: Context): File {
    val configFile = File(context.filesDir, "singbox_active.json")

    try {
      val conn = URL(CONFIG_URL).openConnection() as HttpURLConnection
      conn.connectTimeout = 8000
      conn.readTimeout = 8000
      val rawJson = conn.inputStream.bufferedReader().use { it.readText() }

      val root = JSONObject(rawJson)

      // 1. Настраиваем Inbound строго на SOCKS5 127.0.0.1:10808
      val socksInbound = JSONObject().apply {
        put("type", "socks")
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("listen_port", LOCAL_PORT)
      }
      root.put("inbounds", JSONArray().apply { put(socksInbound) })

      // 2. Находим главный тег группы прокси (auto, urltest или первый узел)
      val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
      var targetTag = "auto"
      if (outbounds.length() > 0) {
        var found = false
        for (i in 0 until outbounds.length()) {
          val ob = outbounds.getJSONObject(i)
          val type = ob.optString("type")
          if (type == "urltest" || type == "selector") {
            targetTag = ob.getString("tag")
            found = true
            break
          }
        }
        if (!found) {
          targetTag = outbounds.getJSONObject(0).getString("tag")
        }
      }

      // 3. Гарантируем, что весь трафик из socks-in идет строго через VLESS-прокси
      val route = root.optJSONObject("route") ?: JSONObject()
      val rules = route.optJSONArray("rules") ?: JSONArray()
      
      val forceProxyRule = JSONObject().apply {
        put("inbound", JSONArray().apply { put("socks-in") })
        put("outbound", targetTag)
      }

      // Вставляем наше правило в самое начало списка правил
      val newRules = JSONArray().apply {
        put(forceProxyRule)
        for (i in 0 until rules.length()) {
          put(rules.get(i))
        }
      }

      route.put("rules", newRules)
      route.put("final", targetTag)
      root.put("route", route)

      configFile.writeText(root.toString(2))
    } catch (e: Exception) {
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
  private fun showToast(context: Context, msg: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }
}
