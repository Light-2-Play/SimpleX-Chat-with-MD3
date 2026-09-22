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

        // Вычитываем логи в фоне, чтобы буфер ОС не переполнялся
        thread(name = "SingBoxLogReader") {
          try {
            proc.inputStream.bufferedReader().useLines { lines ->
              lines.forEach { line ->
                Log.d(TAG, line)
              }
            }
          } catch (e: Exception) {
            // Игнорируем закрытие потока
          }
        }

        // Проверяем доступность локального сокета 10808
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
            // Порт еще не открыт, продолжаем опрос
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

    try {
      val conn = URL(CONFIG_URL).openConnection() as HttpURLConnection
      conn.connectTimeout = 8000
      conn.readTimeout = 8000
      val rawJson = conn.inputStream.bufferedReader().use { it.readText() }

      val sourceRoot = JSONObject(rawJson)
      val sourceOutbounds = sourceRoot.optJSONArray("outbounds") ?: JSONArray()

      val root = JSONObject()

      // 1. Логи
      root.put("log", JSONObject().apply {
        put("level", "warn")
      })

      // 2. Входной сокет SOCKS5 на 127.0.0.1:10808
      val socksInbound = JSONObject().apply {
        put("type", "socks")
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("listen_port", LOCAL_PORT)
        put("sniff", true)
      }
      root.put("inbounds", JSONArray().apply { put(socksInbound) })

      // 3. DNS: Quad9 (Основной) + Google (Резервный) через DoH (порт 443)
      val dns = JSONObject().apply {
        val servers = JSONArray().apply {
          // Quad9 DoH (Швейцария, приватность, фильтрация фишинга)
          put(JSONObject().apply {
            put("tag", "quad9-doh")
            put("address", "https://9.9.9.9/dns-query")
          })
          // Google DoH (Высокая скорость и глобальный аптайм)
          put(JSONObject().apply {
            put("tag", "google-doh")
            put("address", "https://8.8.8.8/dns-query")
          })
        }
        put("servers", servers)
        put("strategy", "prefer_ipv4")
      }
      root.put("dns", dns)

      // 4. Очищаем Outbounds и берем первый рабочий сервер
      val cleanOutbounds = JSONArray()
      var selectedTag = ""

      for (i in 0 until sourceOutbounds.length()) {
        val ob = sourceOutbounds.getJSONObject(i)
        val type = ob.optString("type")
        val tag = ob.optString("tag")

        if (type == "direct" || type == "block" || type == "dns") continue

        cleanOutbounds.put(ob)
        if (selectedTag.isEmpty()) {
          selectedTag = tag
        }
      }

      cleanOutbounds.put(JSONObject().apply {
        put("type", "direct")
        put("tag", "direct")
      })

      root.put("outbounds", cleanOutbounds)

      // 5. Маршрутизация: socks-in направляется строго в рабочий VLESS-узел
      val route = JSONObject().apply {
        val rules = JSONArray().apply {
          put(JSONObject().apply {
            put("inbound", JSONArray().apply { put("socks-in") })
            put("outbound", selectedTag)
          })
        }
        put("rules", rules)
        put("final", selectedTag)
        put("auto_detect_interface", true)
      }
      root.put("route", route)

      configFile.writeText(root.toString(2))
    } catch (e: Exception) {
      if (!configFile.exists()) throw e
    }

    return configFile
  }

      // 4. Очищаем Outbounds от мусора и находим рабочий узел
      val cleanOutbounds = JSONArray()
      var selectedTag = ""

      for (i in 0 until sourceOutbounds.length()) {
        val ob = sourceOutbounds.getJSONObject(i)
        val type = ob.optString("type")
        val tag = ob.optString("tag")

        // Пропускаем TUN, блокировщики рекламы и старые селекторы
        if (type == "direct" || type == "block" || type == "dns") continue

        cleanOutbounds.put(ob)
        if (selectedTag.isEmpty()) {
          selectedTag = tag // Берем первый валидный сервер
        }
      }

      // Добавляем прямой выход как fallback
      cleanOutbounds.put(JSONObject().apply {
        put("type", "direct")
        put("tag", "direct")
      })

      root.put("outbounds", cleanOutbounds)

      // 5. Прямой роутинг: всё из socks-in идет строго в selectedTag
      val route = JSONObject().apply {
        val rules = JSONArray().apply {
          put(JSONObject().apply {
            put("inbound", JSONArray().apply { put("socks-in") })
            put("outbound", selectedTag)
          })
        }
        put("rules", rules)
        put("final", selectedTag)
        put("auto_detect_interface", true)
      }
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
