package com.chatty.fr

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chatty.fr.data.Contact
import com.chatty.fr.sms.Notifications
import com.chatty.fr.ui.BlockedScreen
import com.chatty.fr.ui.BroadcastScreen
import com.chatty.fr.ui.ConversationDetailsScreen
import com.chatty.fr.ui.LockScreen
import com.chatty.fr.ui.ScheduledScreen
import com.chatty.fr.ui.StarredScreen
import com.chatty.fr.ui.ChatScreen
import com.chatty.fr.ui.ConversationListScreen
import com.chatty.fr.ui.NewConversationScreen
import com.chatty.fr.ui.OnboardingScreen
import com.chatty.fr.ui.SettingsScreen
import com.chatty.fr.ui.theme.ChattyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Inbox : Screen
    data object Archived : Screen
    data object Settings : Screen
    data class New(val text: String? = null) : Screen
    data class Chat(val threadId: Long, val address: String, val text: String? = null) : Screen
    data class Broadcast(val recipients: List<Contact>, val text: String?) : Screen
    data class Details(val threadId: Long, val address: String) : Screen
    data object Starred : Screen
    data object Scheduled : Screen
    data object Blocked : Screen
}

class MainActivity : FragmentActivity() {

    private val vm: ChattyViewModel by viewModels()
    private val isDefault = mutableStateOf(false)
    private val unlocked = mutableStateOf(false)
    private var prompting = false

    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private fun lockAvailable() =
        BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    private fun needsUnlock() = vm.store.appLock && lockAvailable() && !unlocked.value

    private fun promptUnlock() {
        if (prompting || !needsUnlock()) return
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                unlocked.value = true
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Chatty")
                .setSubtitle("Vos messages sont protégés")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkRole()
        if (isDefault.value) askOptionalPermissions()
    }
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkRole()
        if (isDefault.value) {
            vm.start()
            askOptionalPermissions()
        }
        pendingIntent.value = intent

        setContent {
            val version by vm.store.version.collectAsState()
            ChattyTheme(
                dynamicColor = vm.store.dynamicColor.also { version },
                themeMode = vm.store.themeMode,
                textScale = vm.store.textScale,
            ) {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        !isDefault.value -> OnboardingScreen(onMakeDefault = ::requestRole)
                        vm.store.appLock.also { version } && lockAvailable() && !unlocked.value -> LockScreen(::promptUnlock)
                        else -> App()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    override fun onStop() {
        super.onStop()
        // Reverrouille dès qu'on quitte l'appli (sauf pendant une demande système).
        if (!isChangingConfigurations && !prompting) unlocked.value = false
    }

    override fun onResume() {
        super.onResume()
        if (needsUnlock()) promptUnlock()
        val was = isDefault.value
        checkRole()
        if (isDefault.value) vm.start() else if (was) vm.refresh()
    }

    private fun checkRole() {
        val rm = getSystemService(RoleManager::class.java)
        isDefault.value = rm?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
    }

    private fun askOptionalPermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionRequest.launch(perms.toTypedArray())
    }

    @Composable
    private fun App() {
        val stack = remember { mutableStateListOf<Screen>(Screen.Inbox) }
        val scope = rememberCoroutineScope()
        val incoming by pendingIntent.collectAsState()

        var goingBack by remember { mutableStateOf(false) }
        fun push(s: Screen) { goingBack = false; stack.add(s) }
        fun pop() { goingBack = true; if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
        fun openChat(address: String, threadId: Long? = null, text: String? = null) {
            scope.launch {
                val id = threadId ?: vm.threadIdFor(address)
                // Évite d'empiler deux fois la même conversation.
                stack.removeAll { it !is Screen.Inbox && it !is Screen.Archived }
                push(Screen.Chat(id, address, text))
            }
        }

        LaunchedEffect(incoming) {
            val intent = incoming ?: return@LaunchedEffect
            pendingIntent.value = null
            handleIntent(intent, ::openChat) { push(it) }
        }

        BackHandler(enabled = stack.size > 1) { pop() }

        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = {
                if (goingBack) {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                }
            },
            label = "nav",
        ) { screen ->
            when (screen) {
                Screen.Inbox, Screen.Archived -> ConversationListScreen(
                    vm = vm,
                    archivedMode = screen == Screen.Archived,
                    onOpen = { openChat(it.address, it.threadId) },
                    onOpenMessage = { openChat(it.address, it.threadId) },
                    onNew = { push(Screen.New()) },
                    onArchived = { push(Screen.Archived) },
                    onSettings = { push(Screen.Settings) },
                    onBack = ::pop,
                    onStarred = { push(Screen.Starred) },
                    onScheduled = { push(Screen.Scheduled) },
                )
                Screen.Settings -> SettingsScreen(
                    vm.store, onBack = ::pop,
                    onBlocked = { push(Screen.Blocked) },
                    lockAvailable = lockAvailable(),
                )
                Screen.Starred -> StarredScreen(vm, onOpen = { openChat(it.address, it.threadId) }, onBack = ::pop)
                Screen.Scheduled -> ScheduledScreen(vm, onOpen = { openChat(it.address, it.threadId) }, onBack = ::pop)
                Screen.Blocked -> BlockedScreen(vm, onBack = ::pop)
                is Screen.Details -> ConversationDetailsScreen(
                    vm, screen.threadId, screen.address,
                    onBack = ::pop,
                    onDeleted = { goingBack = true; stack.removeAll { it !is Screen.Inbox } },
                )
                is Screen.New -> NewConversationScreen(
                    vm = vm,
                    onBack = ::pop,
                    onStart = { contacts ->
                        if (contacts.size == 1) openChat(contacts.first().number, text = screen.text)
                        else push(Screen.Broadcast(contacts, screen.text))
                    },
                    onStartGroup = { contacts ->
                        openChat(contacts.joinToString(",") { it.number }, text = screen.text)
                    },
                )
                is Screen.Chat -> ChatScreen(
                    vm = vm,
                    threadId = screen.threadId,
                    address = screen.address,
                    initialText = screen.text,
                    onBack = ::pop,
                    onDetails = { push(Screen.Details(screen.threadId, screen.address)) },
                )
                is Screen.Broadcast -> BroadcastScreen(
                    vm = vm,
                    recipients = screen.recipients,
                    initialText = screen.text,
                    onDone = { goingBack = true; stack.removeAll { it !is Screen.Inbox } },
                    onBack = ::pop,
                )
            }
        }
    }

    private fun handleIntent(intent: Intent, openChat: (String, Long?, String?) -> Unit, push: (Screen) -> Unit) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD_ID, -1)
        val address = intent.getStringExtra(Notifications.EXTRA_ADDRESS)
        when {
            threadId > 0 && address != null -> openChat(address, threadId, null)
            intent.action == Intent.ACTION_SENDTO || (intent.action == Intent.ACTION_SEND && intent.data != null) -> {
                val data = intent.data ?: return
                val recipients = parseRecipients(data)
                val body = intent.getStringExtra("sms_body")
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: data.getQueryParameterSafe("body")
                when {
                    recipients.size == 1 -> openChat(recipients.first(), null, body)
                    recipients.size > 1 -> push(Screen.Broadcast(recipients.map { Contact(null, it) }, body))
                    else -> push(Screen.New(body))
                }
            }
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                push(Screen.New(intent.getStringExtra(Intent.EXTRA_TEXT)))
            }
        }
    }

    private fun parseRecipients(uri: Uri): List<String> =
        Uri.decode(uri.schemeSpecificPart.orEmpty().substringBefore('?'))
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun Uri.getQueryParameterSafe(key: String): String? {
        val query = schemeSpecificPart?.substringAfter('?', "") ?: return null
        return query.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.let(Uri::decode)
    }
}
