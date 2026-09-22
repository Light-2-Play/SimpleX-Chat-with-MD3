package chat.simplex.app
import chat.simplex.common.ui.theme.MonetPalette
import chat.simplex.common.ui.theme.getMonetPalette
import androidx.compose.ui.graphics.Color
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.*
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import chat.simplex.app.model.NtfManager
import chat.simplex.app.model.NtfManager.getUserIdFromIntent
import chat.simplex.common.*
import chat.simplex.common.helpers.*
import chat.simplex.common.model.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.platform.*
import chat.simplex.res.MR
import java.lang.ref.WeakReference
import chat.simplex.app.SingBoxService
import chat.simplex.common.views.chatlist.ByeDpiBridge
// Глобальный обработчик для открытия диалога из Compose UI
var openByeDpiDialog: (() -> Unit)? = null

class MainActivity: FragmentActivity() {
  companion object {
    const val OLD_ANDROID_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
  }

override fun onCreate(savedInstanceState: Bundle?) {
    mainActivity = WeakReference(this)
    super.onCreate(savedInstanceState)

    // 1. Привязываем открытие диалога серверов к кнопкам тулбара:
    openByeDpiDialog = {
      showSingBoxDialog()
    }
    ByeDpiBridge.showDialog = {
      showSingBoxDialog()
    }

    // 2. Динамические цвета Monet (Android 12+):
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      getMonetPalette = { isDark ->
        if (isDark) {
          MonetPalette(
            // Основной акцент
            primary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_200)),
            // Светлая подложка для круглых кнопок:
            primaryVariant = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_200)),
            background = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_900)),
            surface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_800)),
            // onPrimary: стал светлее на 30% (тон 600 вместо 900)
            onPrimary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_600)),
            onBackground = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_100)),
            onSurface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_100)),
            sentMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent2_700)),
            // sentQuote: стал светлее на 30% (тон 600 вместо 900)
            sentQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_600)),
            receivedMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_800)),
            receivedQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_700)),
            // primaryVariant2 (иконки кнопок): стал светлее на 30% (тон 600 вместо 900)
            primaryVariant2 = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_600))
          )
        } else {
          MonetPalette(
            primary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_600)),
            primaryVariant = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_700)),
            background = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_50)),
            surface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_100)),
            onPrimary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_0)),
            onBackground = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_900)),
            onSurface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_900)),
            sentMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent2_100)),
            sentQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent2_200)),
            receivedMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_100)),
            receivedQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_200)),
            primaryVariant2 = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_500))
          )
        }
      }
    }
   
    // 3. Родная инициализация темы и окружения SimpleX:
    platform.androidSetNightModeIfSupported()
    val c = CurrentColors.value.colors
    platform.androidSetStatusAndNavigationBarAppearance(c.isLight, c.isLight)
    applyAppLocale(ChatModel.controller.appPrefs.appLanguage)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    if (savedInstanceState == null) {
      processNotificationIntent(intent)
      processIntent(intent)
      processExternalIntent(intent)
    }

    if (ChatController.appPrefs.privacyProtectScreen.get()) {
      Log.d(TAG, "onCreate: set FLAG_SECURE")
      window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
      )
    }

    // 4. Безопасный единственный запуск SingBox с задержкой (не блокирует сплеш-скрин):
    try {
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
          SingBoxService.start(this)
        } catch (e: Throwable) {
          android.util.Log.e("SimpleXMod", "Ошибка запуска SingBox", e)
        }
      }, 1000)
    } catch (e: Throwable) {
      android.util.Log.e("SimpleXMod", "Сбой вызова Handler", e)
    }

    enableEdgeToEdge()

    setContent {
      AppScreen()
    }

    SimplexApp.context.schedulePeriodicServiceRestartWorker()
    SimplexApp.context.schedulePeriodicWakeUp()
  }// <--- ВОТ ЗДЕСЬ законно закрывается метод onCreate

  // Теперь объявляется функция диалога (ПОСЛЕ onCreate, но ВНУТРИ класса MainActivity):
 private fun showSingBoxDialog() {
    val options = arrayOf(
      "25 серверов (рекомендуется)",
      "50 серверов",
      "100 серверов",
      "Все доступные"
    )
    val limits = intArrayOf(25, 50, 100, 0)

    val currentLimit = SingBoxService.getServerLimit(this)
    var selectedIndex = limits.indexOf(currentLimit).let { if (it == -1) 0 else it }

    val statusText = if (SingBoxService.isRunning) "● VLESS активен (порт 20808)" else "○ VLESS выключен"

    android.app.AlertDialog.Builder(this)
      // Переносим статус в заголовок, чтобы не блокировать список:
      .setTitle("Настройки VLESS Proxy\n$statusText")
      // Убираем .setMessage(...) — теперь этот список гарантированно отобразится:
      .setSingleChoiceItems(options, selectedIndex) { _, which ->
        selectedIndex = which
      }
      .setPositiveButton(if (SingBoxService.isRunning) "Перезапустить" else "Включить") { _, _ ->
        val newLimit = limits[selectedIndex]
        SingBoxService.setServerLimit(this, newLimit)

        if (SingBoxService.isRunning) {
          SingBoxService.restart(this)
        } else {
          SingBoxService.start(this)
        }
      }
      .setNegativeButton("Отключить") { _, _ ->
        SingBoxService.stop()
      }
      .setNeutralButton("Отмена", null)
      .show()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    processIntent(intent)
    processExternalIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    AppLock.recheckAuthState()
  }

  override fun onPause() {
    super.onPause()
    /**
     * When new activity is created after a click on notification, the old one receives onPause before
     * recreation but receives onStop after recreation. So using both (onPause and onStop) to prevent
     * unwanted multiple auth dialogs from [runAuthenticate]
     * */
    AppLock.appWasHidden()
  }

  override fun onStop() {
    super.onStop()
    VideoPlayerHolder.stopAll()
    AppLock.appWasHidden()
  }

  override fun onDestroy() {
    super.onDestroy()
    SingBoxService.stop()
  }

  override fun onBackPressed() {
    val canFinishActivity = (
        onBackPressedDispatcher.hasEnabledCallbacks() // Has something to do in a backstack
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R // Android 11 or above
            || isTaskRoot // there are still other tasks after we reach the main (home) activity
        ) && SimplexApp.context.chatModel.sharedContent.value !is SharedContent.Forward
    if (canFinishActivity) {
      // https://medium.com/mobile-app-development-publication/the-risk-of-android-strandhogg-security-issue-and-how-it-can-be-mitigated-80d2ddb4af06
      super.onBackPressed()
    }

    if (!onBackPressedDispatcher.hasEnabledCallbacks() && ChatController.appPrefs.performLA.get()) {
      // When pressed Back and there is no one wants to process the back event, clear auth state to force re-auth on launch
      AppLock.clearAuthState()
      AppLock.laFailed.value = true
    }
    if (!onBackPressedDispatcher.hasEnabledCallbacks()) {
      val sharedContent = chatModel.sharedContent.value
      // Drop shared content
      chatModel.sharedContent.value = null
      if (sharedContent is SharedContent.Forward) {
        chatModel.chatId.value = sharedContent.fromChatInfo.id
      }
      if (canFinishActivity) {
        finish()
      }
    }
  }
}

fun processNotificationIntent(intent: Intent?) {
  val userId = getUserIdFromIntent(intent)
  when (intent?.action) {
    NtfManager.OpenChatAction -> {
      val chatId = intent.getStringExtra("chatId")
      Log.d(TAG, "processNotificationIntent: OpenChatAction $chatId")
      if (chatId != null) {
        ntfManager.openChatAction(userId, chatId)
      }
    }
    NtfManager.ShowChatsAction -> {
      Log.d(TAG, "processNotificationIntent: ShowChatsAction")
      ntfManager.showChatsAction(userId)
    }
    NtfManager.AcceptCallAction -> {
      val chatId = intent.getStringExtra("chatId")
      if (chatId == null || chatId == "") return
      Log.d(TAG, "processNotificationIntent: AcceptCallAction $chatId")
      ntfManager.acceptCallAction(chatId)
    }
  }
}

fun processIntent(intent: Intent?) {
  when (intent?.action) {
    "android.intent.action.VIEW" -> {
      val uri = intent.data
      if (uri != null) {
        chatModel.appOpenUrl.value = null to uri.toString()
      } else {
        AlertManager.shared.showAlertMsg(generalGetString(MR.strings.error_parsing_uri_title), generalGetString(MR.strings.error_parsing_uri_desc))
      }
    }
  }
}

fun processExternalIntent(intent: Intent?) {
  when (intent?.action) {
    Intent.ACTION_SEND -> {
      // Close active chat and show a list of chats
      chatModel.chatId.value = null
      chatModel.clearOverlays.value = true
      when {
        intent.type == "text/plain" -> {
          val text = intent.getStringExtra(Intent.EXTRA_TEXT)
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            // Shared file that contains plain text, like `*.log` file
            chatModel.sharedContent.value = SharedContent.File(text ?: "", uri.toURI())
          } else if (text != null) {
            // Shared just a text
            chatModel.sharedContent.value = SharedContent.Text(text)
          }
        }
        isMediaIntent(intent) -> {
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.Media(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", listOf(uri.toURI()))
          } // All other mime types
        }
        else -> {
          val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
          if (uri != null) {
            if (uri.scheme != "content") return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.File(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", uri.toURI())
          }
        }
      }
    }
    Intent.ACTION_SEND_MULTIPLE -> {
      // Close active chat and show a list of chats
      chatModel.chatId.value = null
      chatModel.clearOverlays.value = true
      Log.e(TAG, "ACTION_SEND_MULTIPLE ${intent.type}")
      when {
        isMediaIntent(intent) -> {
          val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM) as? List<Uri>
          if (uris != null) {
            if (uris.any { it.scheme != "content" }) return showWrongUriAlert()
            chatModel.sharedContent.value = SharedContent.Media(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "", uris.map { it.toURI() })
          } // All other mime types
        }
        else -> {}
      }
    }
  }
}

fun isMediaIntent(intent: Intent): Boolean =
  intent.type?.startsWith("image/") == true || intent.type?.startsWith("video/") == true

//fun testJson() {
//  val str: String = """
//  """.trimIndent()
//
//  println(json.decodeFromString<APIResult>(str))
//}
