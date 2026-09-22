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

    // 1. Привязываем вызов диалога к кнопкам тулбара:
    ByeDpiBridge.showDialog = {
      showSingBoxDialog()
    }
    openByeDpiDialog = {
      showSingBoxDialog()
    }

    // 2. Автостарт сервиса SingBox при запуске:
    SingBoxService.start(this)

    // 3. Динамические цвета Monet (ДОЛЖНЫ быть внутри onCreate, чтобы работал getColor):
    getMonetPalette = { isDark ->
      if (isDark) {
        MonetPalette(
          primary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_200)),
          primaryVariant = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_300)),
          background = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_900)),
          surface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_800)),
          onPrimary = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_800)),
          onBackground = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_100)),
          onSurface = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral1_100)),
          sentMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent2_700)),
          sentQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent2_800)),
          receivedMessage = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_800)),
          receivedQuote = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_neutral2_700)),
          primaryVariant2 = androidx.compose.ui.graphics.Color(getColor(android.R.color.system_accent1_100))
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

    // ДАЛЬШЕ идет оригинальный код SimpleX внутри onCreate (window, intent, setContent и т.д.)
    // НЕ закрывайте onCreate здесь! Метод закроется своей родной скобкой ПОСЛЕ setContent.

    // Автостарт при запуске приложения:
    SingBoxService.start(this)
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      getMonetPalette = { isDark ->
        if (isDark) {
          MonetPalette(
            primary = Color(getColor(android.R.color.system_accent1_200)),
            primaryVariant = Color(getColor(android.R.color.system_accent1_300)),
            background = Color(getColor(android.R.color.system_neutral1_900)),
            surface = Color(getColor(android.R.color.system_neutral1_800)),
            onPrimary = Color(getColor(android.R.color.system_accent1_900)),
            onBackground = Color(getColor(android.R.color.system_neutral1_100)),
            onSurface = Color(getColor(android.R.color.system_neutral1_100)),
            sentMessage = Color(getColor(android.R.color.system_accent1_700)),
            sentQuote = Color(getColor(android.R.color.system_accent1_600)),
            receivedMessage = Color(getColor(android.R.color.system_neutral2_700)),
            receivedQuote = Color(getColor(android.R.color.system_neutral2_600)),
            primaryVariant2 = Color(getColor(android.R.color.system_accent1_200))
          )
        } else {
          MonetPalette(
            primary = Color(getColor(android.R.color.system_accent1_600)),
            primaryVariant = Color(getColor(android.R.color.system_accent1_700)),
            background = Color(getColor(android.R.color.system_neutral2_100)),
            surface = Color(getColor(android.R.color.system_neutral1_100)),
            onPrimary = Color.White,
            onBackground = Color(getColor(android.R.color.system_neutral1_900)),
            onSurface = Color(getColor(android.R.color.system_neutral1_900)),
            sentMessage = Color(getColor(android.R.color.system_accent1_100)),
            sentQuote = Color(getColor(android.R.color.system_accent1_200)),
            receivedMessage = Color(getColor(android.R.color.system_neutral2_200)), 
            receivedQuote = Color(getColor(android.R.color.system_neutral2_300)),
            primaryVariant2 = Color(getColor(android.R.color.system_accent1_600))
          )
        }
      }
    }

    platform.androidSetNightModeIfSupported()
    val c = CurrentColors.value.colors
    platform.androidSetStatusAndNavigationBarAppearance(c.isLight, c.isLight)
    applyAppLocale(ChatModel.controller.appPrefs.appLanguage)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    super.onCreate(savedInstanceState)

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

    // Запуск SingBox VLESS SOCKS5 сервиса
    SingBoxService.start(this)

    // Привязываем клик по замочку к открытию окна настроек/серверов:
    openByeDpiDialog = {
      showSingBoxDialog()
    }
    ByeDpiBridge.showDialog = {
      showSingBoxDialog()
    }
    
    enableEdgeToEdge()

    setContent {
      AppScreen()
    }

    // Родной хвост onCreate SimpleX (должен быть ВНУТРИ onCreate):
    SimplexApp.context.schedulePeriodicServiceRestartWorker()
    SimplexApp.context.schedulePeriodicWakeUp()
  } // <--- ВОТ ЗДЕСЬ законно закрывается метод onCreate

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
      .setTitle("Настройки VLESS Proxy")
      .setMessage("Статус: $statusText\n\nКоличество серверов для тестирования:")
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
