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

  // Список ссылок на подписки в порядке приоритета:
  // 1. Быстрое CDN-зеркало новой подписки Au1rxx (без блокировок)
  // 2. Прямой GitHub raw адрес Au1rxx
  // 3. Резервный источник awesome-vpn
  private val SUBSCRIPTION_URLS = listOf(
    "https://cdn.jsdelivr.net/gh/Au1rxx/free-vpn-subscriptions@main/output/singbox.json",
    "https://github.com/Au1rxx/free-vpn-subscriptions/raw/main/output/singbox.json",
    "https://cdn.jsdelivr.net/gh/awesome-vpn/awesome-vpn@master/sing-box.json"
  )

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

        // Считываем поток вывода в фоне во избежание переполнения буфера
        thread(name = "SingBoxLogReader") {
          try {
            proc.inputStream.bufferedReader().useLines { lines ->
              lines.forEach { line ->
                Log.d(TAG, line)
              }
            }
          } catch (e: Exception) {
            // Закрытие потока
          }
        }

        // Проверяем доступность локального сокета 127.0.0.1:10808
        var portOpen = false
        for (i in 0 until 20) {
          Thread.sleep(300)
          try {
            Socket().use { s ->
              s.connect(InetSocketAddress("127.0.0.1", LOCAL_PORT), 300)
              portOpen = true
            }
            break
          } catch (e: Exception) {
            // Ждем запуска сокета
          }
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
    } catch (e: Exception) {
      // Игнорируем
    }
    isRunning = false
  }

  private fun prepareConfig(context: Context): File {
    val configFile = File(context.filesDir, "singbox_active.json")
    var rawJson: String? = null

    // Пробуем скачать конфигурацию из доступных источников по очереди
    for (url in SUBSCRIPTION_URLS) {
      try {
        val downloaded = downloadUrl(url)
        if (downloaded.isNotBlank()) {
          rawJson = downloaded
          break
        }
      } catch (e: Exception) {
        Log.w(TAG, "Не удалось загрузить $url: ${e.message}")
      }
    }

    if (rawJson.isNullOrBlank()) {
      if (configFile.exists()) {
        return configFile // Используем ранее сохранённый кэш, если сети нет
      }
      throw IllegalStateException("Не удалось загрузить ни одну подписку")
    }

    val sourceRoot = JSONObject(rawJson)
    val sourceOutbounds = sourceRoot.optJSONArray("outbounds") ?: JSONArray()

    val root = JSONObject()

    // 1. Логи
    root.put("log", JSONObject().apply {
      put("level", "warn")
    })

    // 2. Входной локальный SOCKS5 на 127.0.0.1:10808
    val socksInbound = JSONObject().apply {
      put("type", "socks")
      put("tag", "socks-in")
      put("listen", "127.0.0.1")
      put("listen_port", LOCAL_PORT)
      put("sniff", true)
    }
    root.put("inbounds", JSONArray().apply { put(socksInbound) })

    // 3. DNS: Quad9 + Google через DoH (порт 443)
    val dns = JSONObject().apply {
      val servers = JSONArray().apply {
        put(JSONObject().apply {
          put("tag", "quad9-doh")
          put("address", "https://9.9.9.9/dns-query")
        })
        put(JSONObject().apply {
          put("tag", "google-doh")
          put("address", "https://8.8.8.8/dns-query")
        })
      }
      put("servers", servers)
      put("strategy", "prefer_ipv4")
    }
    root.put("dns", dns)

    // 4. Фильтруем серверы и создаем группу urltest (автовыбор живого узла)
    val cleanOutbounds = JSONArray()
    val proxyTags = JSONArray()

    for (i in 0 until sourceOutbounds.length()) {
      val ob = sourceOutbounds.getJSONObject(i)
      val type = ob.optString("type")
      val tag = ob.optString("tag")

      if (type == "direct" || type == "block" || type == "dns" || type == "urltest" || type == "selector") continue

      cleanOutbounds.put(ob)
      proxyTags.put(tag)
    }

    val targetTag = if (proxyTags.length() > 0) {
      val urlTestGroup = JSONObject().apply {
        put("type", "urltest")
        put("tag", "auto")
        put("outbounds", proxyTags)
        put("url", "https://cp.cloudflare.com/generate_204")
        put("interval", "3m")
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

    // 5. Маршрутизация: socks-in направляется в auto-группу
    val route = JSONObject().apply {
      val rules = JSONArray().apply {
        put(JSONObject().apply {
          put("inbound", JSONArray().apply { put("socks-in") })
          put("outbound", targetTag)
        })
      }
      put("rules", rules)
      put("final", targetTag)
      put("auto_detect_interface", true)
    }
    root.put("route", route)

    configFile.writeText(root.toString(2))
    return configFile
  }

  // Скачивание по HTTP с обработкой возможных редиректов (301, 302, 307)
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
    throw IllegalStateException("Ошибка ответа сети по адресу: $urlString")
  }

  private fun showToast(context: Context, msg: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }
}
