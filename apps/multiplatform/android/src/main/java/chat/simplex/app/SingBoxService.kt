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
  private const val LOCAL_PORT = 20808
  private var process: Process? = null

  private val SUBSCRIPTION_URLS = listOf(
    "https://cdn.jsdelivr.net/gh/Au1rxx/free-vpn-subscriptions@main/output/singbox.json",
    "https://github.com/Au1rxx/free-vpn-subscriptions/raw/main/output/singbox.json",
    "https://cdn.jsdelivr.net/gh/awesome-vpn/awesome-vpn@master/sing-box.json",
    "https://cdn.jsdelivr.net/gh/0xRadikal/Free-v2ray-Configs@main/verified/singbox.json",
        "https://raw.githubusercontent.com/0xRadikal/Free-v2ray-Configs/main/verified/singbox.json"
  )

private const val PREFS_NAME = "singbox_preferences"
  private const val KEY_SERVER_LIMIT = "server_limit"
  const val DEFAULT_SERVER_LIMIT = 25 // По умолчанию отбираем 25 серверов

  fun getServerLimit(context: Context): Int {
    val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return sp.getInt(KEY_SERVER_LIMIT, DEFAULT_SERVER_LIMIT)
  }

  fun setServerLimit(context: Context, limit: Int) {
    val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    sp.edit().putInt(KEY_SERVER_LIMIT, limit).apply()
  }
  
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

  fun restart(context: Context) {
    stop()
    thread(name = "SingBoxRestarter") {
      Thread.sleep(600) // даем ОС полсекунды полностью освободить сокет 20808
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

        try {
          binaryFile.setExecutable(true)
        } catch (_: Exception) {}

        val pb = ProcessBuilder(
          binaryFile.absolutePath,
          "run",
          "-c",
          configFile.absolutePath
        )
        pb.redirectErrorStream(true)
        val proc = pb.start()
        process = proc

        var lastLog = ""
        thread(name = "SingBoxLogReader") {
          try {
            proc.inputStream.bufferedReader().useLines { lines ->
              lines.forEach { line ->
                Log.d(TAG, line)
                lastLog = line
              }
            }
          } catch (_: Exception) {}
        }

        // Проверяем открытие сокета 127.0.0.1:20808
        var portOpen = false
        for (i in 0 until 25) {
          Thread.sleep(300)
          try {
            Socket().use { s ->
              s.connect(InetSocketAddress("127.0.0.1", LOCAL_PORT), 300)
              portOpen = true
            }
            break
          } catch (_: Exception) {
            // Процесс мог упасть во время ожидания
            if (!proc.isAlive) break
          }
        }

        if (portOpen) {
          isRunning = true
          showToast(context, "VLESS подключен ($LOCAL_PORT)")
        } else {
          val errorDetail = if (!proc.isAlive) {
            "вылет (код ${proc.exitValue()}): $lastLog"
          } else {
            "таймаут порта $LOCAL_PORT"
          }
          stop()
          showToast(context, "Ошибка: $errorDetail")
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
    var rawJson: String? = null

    for (url in SUBSCRIPTION_URLS) {
      try {
        val downloaded = downloadUrl(url)
        if (downloaded.isNotBlank()) {
          rawJson = downloaded
          break
        }
      } catch (e: Exception) {
        Log.w(TAG, "Ошибка загрузки $url: ${e.message}")
      }
    }

    if (rawJson.isNullOrBlank()) {
      if (configFile.exists()) {
        return configFile
      }
      throw IllegalStateException("Не удалось загрузить подписку")
    }

    val sourceRoot = JSONObject(rawJson)
    val sourceOutbounds = sourceRoot.optJSONArray("outbounds") ?: JSONArray()

    val root = JSONObject()

    // 1. Логирование
    root.put("log", JSONObject().apply {
      put("level", "warn")
    })

    // 2. Входящий SOCKS5-интерфейс
    val socksInbound = JSONObject().apply {
      put("type", "socks")
      put("tag", "socks-in")
      put("listen", "127.0.0.1")
      put("listen_port", LOCAL_PORT)
    }
    root.put("inbounds", JSONArray().apply { put(socksInbound) })

    // 3. DNS: Прямой опрос без зацикливания через detour: direct
    val dns = JSONObject().apply {
      val servers = JSONArray().apply {
        put(JSONObject().apply {
          put("tag", "quad9-doh")
          put("address", "https://9.9.9.9/dns-query")
          put("detour", "direct")
        })
        put(JSONObject().apply {
          put("tag", "google-doh")
          put("address", "https://8.8.8.8/dns-query")
          put("detour", "direct")
        })
      }
      put("servers", servers)
      put("strategy", "prefer_ipv4")
    }
    root.put("dns", dns)

    // 4. Умная фильтрация и выборка серверов
    val candidateOutbounds = mutableListOf<JSONObject>()

    for (i in 0 until sourceOutbounds.length()) {
      val ob = sourceOutbounds.getJSONObject(i)
      val type = ob.optString("type")

      // Исключаем служебные группы подписки
      if (type == "direct" || type == "block" || type == "dns" || type == "urltest" || type == "selector") continue

      candidateOutbounds.add(ob)
    }

    // Считываем лимит (по умолчанию 25; если передано 0 или значение больше общего числа — берутся все)
    val limit = getServerLimit(context)
    val selectedOutbounds = if (limit in 1 until candidateOutbounds.size) {
      candidateOutbounds.shuffled().take(limit)
    } else {
      candidateOutbounds
    }

    val cleanOutbounds = JSONArray()
    val proxyTags = JSONArray()

    for (ob in selectedOutbounds) {
      cleanOutbounds.put(ob)
      proxyTags.put(ob.optString("tag"))
    }

    // Рабочий эндпоинт проверки Google (не блокируется ТСПУ)
    val targetTag = if (proxyTags.length() > 0) {
      val urlTestGroup = JSONObject().apply {
        put("type", "urltest")
        put("tag", "auto")
        put("outbounds", proxyTags)
        put("url", "https://www.gstatic.com/generate_204")
        put("interval", "2m")
        put("tolerance", 50)
      }
      cleanOutbounds.put(urlTestGroup)
      "auto"
    } else {
      "direct"
    }

    cleanOutbounds.put(JSONObject().apply {
      put("type", "direct")
      put("tag", "direct")
    })

    root.put("outbounds", cleanOutbounds)

    // 5. Маршрутизация: socks-in уходит в auto, а системный трафик ядра (DNS/тесты) идет в direct
    val route = JSONObject().apply {
      val rules = JSONArray().apply {
        put(JSONObject().apply {
          put("inbound", JSONArray().apply { put("socks-in") })
          put("outbound", targetTag)
        })
      }
      put("rules", rules)
      put("final", "direct")
    }
    root.put("route", route)

    configFile.writeText(root.toString(2))
    return configFile
  }

  private fun downloadUrl(urlString: String): String {
    var curUrl = urlString
    for (redirect in 0 until 5) {
      val conn = (URL(curUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "v2rayNG/1.8.5")
      }
      val code = conn.responseCode
      if (code == HttpURLConnection.HTTP_MOVED_PERM ||
        code == HttpURLConnection.HTTP_MOVED_TEMP ||
        code == 307 || code == 308
      ) {
        val loc = conn.getHeaderField("Location") ?: break
        curUrl = loc
        continue
      }
      if (code in 200..299) {
        return conn.inputStream.bufferedReader().use { it.readText() }
      }
      break
    }
    throw IllegalStateException("Ошибка ответа сети: $urlString")
  }

  private fun showToast(context: Context, msg: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }
}
