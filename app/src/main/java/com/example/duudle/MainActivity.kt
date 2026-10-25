package com.example.duudle

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.duudle.ui.theme.DuudleTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class Screen { WELCOME, HOME, DRAW, FRIENDS, VAULT, OVERLAY_DEBUG, VIEW_DOODLE, PROFILE, EDIT_CHARACTER, CHAT }

private data class ReplyTarget(val uid: String, val username: String?)

private data class ChatTarget(val uid: String, val username: String?)

class MainActivity : ComponentActivity() {
    // Set by the overlay's "Draw" button — via onCreate on a cold start, or via
    // onNewIntent when the app is already running. The UI consumes it once.
    private var pendingReply by mutableStateOf<ReplyTarget?>(null)
    // Same idea for tapping a chat notification.
    private var pendingChat by mutableStateOf<ChatTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // After a rotation or process restore the saved screen state wins;
        // re-reading the launch extras would yank the user back to Draw.
        if (savedInstanceState == null) {
            consumeReplyIntent(intent)
            consumeChatIntent(intent)
        }

        setContent {
            DuudleTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE) }
                var currentScreen by rememberSaveable {
                    mutableStateOf(
                        if (!prefs.getString("username", null).isNullOrBlank()) Screen.HOME else Screen.WELCOME
                    )
                }
                var isSigningIn by remember { mutableStateOf(false) }
                var signInError by remember { mutableStateOf<String?>(null) }
                var recipientUid by rememberSaveable { mutableStateOf<String?>(null) }
                var recipientUsername by rememberSaveable { mutableStateOf<String?>(null) }
                var incomingDoodles by remember { mutableStateOf<List<IncomingDoodle>>(emptyList()) }
                var chatUid by rememberSaveable { mutableStateOf<String?>(null) }
                var chatUsername by rememberSaveable { mutableStateOf<String?>(null) }

                LaunchedEffect(pendingReply) {
                    val reply = pendingReply ?: return@LaunchedEffect
                    recipientUid = reply.uid
                    recipientUsername = reply.username
                    currentScreen = Screen.DRAW
                    pendingReply = null
                }

                LaunchedEffect(pendingChat) {
                    val target = pendingChat ?: return@LaunchedEffect
                    pendingChat = null
                    // Not signed up on this install yet: there's no chat to open.
                    if (currentScreen == Screen.WELCOME) return@LaunchedEffect
                    chatUid = target.uid
                    chatUsername = target.username
                    currentScreen = Screen.CHAT
                }

                // Without this, system back closes the app from every screen.
                // The character editor has its own handler (unsaved-changes dialog); this is
                // only its fallback, back to Profile. A chat goes back to Friends.
                BackHandler(enabled = currentScreen != Screen.HOME && currentScreen != Screen.WELCOME) {
                    currentScreen = when (currentScreen) {
                        Screen.EDIT_CHARACTER -> Screen.PROFILE
                        Screen.CHAT -> Screen.FRIENDS
                        else -> Screen.HOME
                    }
                }

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val currentUsername = prefs.getString("username", "") ?: ""

                LaunchedEffect(currentUid) {
                    if (currentUid.isNotBlank() && currentUsername.isNotBlank()) {
                        backfillUsernameLower(currentUid, currentUsername)
                    }
                }

                DisposableEffect(currentUid) {
                    if (currentUid.isBlank()) return@DisposableEffect onDispose {}
                    val listener = listenForIncomingDoodles(currentUid) { doodles ->
                        incomingDoodles = doodles
                    }
                    onDispose { listener.remove() }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.WELCOME -> WelcomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            isLoading = isSigningIn,
                            errorMessage = signInError,
                            onGetStarted = { username ->
                                isSigningIn = true
                                signInError = null
                                signInAndSaveUsername(username) { success, error ->
                                    isSigningIn = false
                                    if (success) {
                                        prefs.edit().putString("username", username).apply()
                                        currentScreen = Screen.HOME
                                    } else {
                                        signInError = error ?: "Something went wrong, try again"
                                    }
                                }
                            }
                        )
                        Screen.HOME -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onDesignDoodleClick = {
                                recipientUid = null
                                recipientUsername = null
                                currentScreen = Screen.DRAW
                            },
                            onFriendsClick = { currentScreen = Screen.FRIENDS },
                            onOverlayDebugClick = { currentScreen = Screen.OVERLAY_DEBUG },
                            onVaultClick = { currentScreen = Screen.VAULT },
                            onMeClick = { currentScreen = Screen.PROFILE },
                            newDoodleFromUsername = incomingDoodles.firstOrNull()?.senderUsername,
                            newItemIsFlashCard = incomingDoodles.firstOrNull()?.cardId != null,
                            onViewDoodle = { currentScreen = Screen.VIEW_DOODLE }
                        )
                        // Keyed on the recipient so a reply arriving while Draw is
                        // already open starts a fresh canvas addressed to the new sender.
                        Screen.DRAW -> key(recipientUid) {
                            DrawScreen(
                                modifier = Modifier.padding(innerPadding),
                                recipientUid = recipientUid,
                                recipientUsername = recipientUsername,
                                currentUid = currentUid,
                                currentUsername = currentUsername,
                                onBack = { currentScreen = Screen.HOME }
                            )
                        }
                        Screen.FRIENDS -> FriendsScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentUid = currentUid,
                            currentUsername = currentUsername,
                            onBack = { currentScreen = Screen.HOME },
                            onSelectFriend = { friend ->
                                recipientUid = friend.uid
                                recipientUsername = friend.username
                                currentScreen = Screen.DRAW
                            },
                            onOverlayDebugClick = { currentScreen = Screen.OVERLAY_DEBUG },
                            onDesignDoodleClick = {
                                recipientUid = null
                                recipientUsername = null
                                currentScreen = Screen.DRAW
                            },
                            onVaultClick = { currentScreen = Screen.VAULT },
                            onMeClick = { currentScreen = Screen.PROFILE },
                            onChatClick = { uid, username ->
                                chatUid = uid
                                chatUsername = username
                                currentScreen = Screen.CHAT
                            }
                        )
                        Screen.CHAT -> {
                            val friendUid = chatUid
                            if (friendUid == null || currentUid.isBlank()) {
                                LaunchedEffect(Unit) { currentScreen = Screen.FRIENDS }
                            } else {
                                // Keyed so opening another chat (e.g. from a notification) starts fresh.
                                key(friendUid) {
                                    ChatScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        currentUid = currentUid,
                                        currentUsername = currentUsername,
                                        friendUid = friendUid,
                                        // Null when opened from a notification: ChatScreen looks it up.
                                        friendUsername = chatUsername,
                                        onBack = { currentScreen = Screen.FRIENDS }
                                    )
                                }
                            }
                        }
                        Screen.VAULT -> VaultScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentUid = currentUid,
                            onBack = { currentScreen = Screen.HOME },
                            onFriendsClick = { currentScreen = Screen.FRIENDS },
                            onDesignDoodleClick = {
                                recipientUid = null
                                recipientUsername = null
                                currentScreen = Screen.DRAW
                            },
                            onMeClick = { currentScreen = Screen.PROFILE },
                            onReply = { uid, username ->
                                recipientUid = uid
                                recipientUsername = username
                                currentScreen = Screen.DRAW
                            }
                        )
                        Screen.VIEW_DOODLE -> {
                            val doodle = incomingDoodles.firstOrNull()
                            if (doodle == null) {
                                LaunchedEffect(Unit) { currentScreen = Screen.HOME }
                            } else {
                                IncomingDoodleScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    doodle = doodle,
                                    currentUid = currentUid,
                                    onSnooze = { currentScreen = Screen.HOME },
                                    onRemove = {
                                        markDoodleStatus(doodle.docId, "removed")
                                        DoodleSnooze.cancel(context, doodle.docId)
                                        currentScreen = Screen.HOME
                                    },
                                    onReply = { senderUid, senderUsername ->
                                        markDoodleStatus(doodle.docId, "removed")
                                        DoodleSnooze.cancel(context, doodle.docId)
                                        recipientUid = senderUid
                                        recipientUsername = senderUsername
                                        currentScreen = Screen.DRAW
                                    }
                                )
                            }
                        }
                        Screen.PROFILE -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentUid = currentUid,
                            currentUsername = currentUsername,
                            onBack = { currentScreen = Screen.HOME },
                            onFriendsClick = { currentScreen = Screen.FRIENDS },
                            onVaultClick = { currentScreen = Screen.VAULT },
                            onDesignDoodleClick = {
                                recipientUid = null
                                recipientUsername = null
                                currentScreen = Screen.DRAW
                            },
                            onOverlayDebugClick = { currentScreen = Screen.OVERLAY_DEBUG },
                            onEditCharacter = { currentScreen = Screen.EDIT_CHARACTER }
                        )
                        Screen.EDIT_CHARACTER -> CharacterEditorScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentUid = currentUid,
                            onClose = { currentScreen = Screen.PROFILE },
                            onSaved = {
                                Toast.makeText(context, "Character saved", Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.PROFILE
                            }
                        )
                        Screen.OVERLAY_DEBUG -> OverlayTestScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReplyIntent(intent)
        consumeChatIntent(intent)
    }

    private fun consumeReplyIntent(intent: Intent?) {
        val uid = intent?.getStringExtra("reply_to_uid") ?: return
        // MainActivity is exported, so extras can come from anywhere: drop malformed uids.
        if (!isAcceptableFriendUid(uid, FirebaseAuth.getInstance().currentUser?.uid)) return
        pendingReply = ReplyTarget(uid, intent.getStringExtra("reply_to_username"))
    }

    private fun consumeChatIntent(intent: Intent?) {
        val uid = intent?.getStringExtra("open_chat_uid") ?: return
        if (!isAcceptableFriendUid(uid, FirebaseAuth.getInstance().currentUser?.uid)) return
        // open_chat_username is deliberately ignored: ChatScreen looks the name up in Firestore.
        pendingChat = ChatTarget(uid, null)
    }
}

fun signInAndSaveUsername(username: String, onResult: (Boolean, String?) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val existingUser = auth.currentUser
    if (existingUser != null) {
        saveUsername(existingUser.uid, username, onResult)
        return
    }
    auth.signInAnonymously()
        .addOnSuccessListener { result ->
            val uid = result.user?.uid
            if (uid == null) onResult(false, "No user ID returned")
            else saveUsername(uid, username, onResult)
        }
        .addOnFailureListener { e -> onResult(false, e.message) }
}

fun usernameKey(username: String): String = username.trim().lowercase()

private fun saveUsername(uid: String, username: String, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    // Names are unique ignoring case, so "Bob" and "bob" can't both exist.
    db.collection("users").whereEqualTo("usernameLower", usernameKey(username)).get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.documents.any { it.id != uid }) {
                onResult(false, "That username is taken, try another")
                return@addOnSuccessListener
            }
            val userData = hashMapOf("username" to username, "usernameLower" to usernameKey(username), "uid" to uid)
            db.collection("users").document(uid).set(userData)
                .addOnSuccessListener {
                    onResult(true, null)
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token -> saveFcmToken(uid, token) }
                        .addOnFailureListener { e ->
                            android.util.Log.e("DuudleFCM", "Failed to get FCM token: ${e.message}")
                        }
                }
                .addOnFailureListener { e -> onResult(false, e.message) }
        }
        .addOnFailureListener { e -> onResult(false, e.message) }
}

/** Accounts created before case-insensitive search lack usernameLower; add it once. */
fun backfillUsernameLower(uid: String, username: String) {
    val userRef = FirebaseFirestore.getInstance().collection("users").document(uid)
    userRef.get().addOnSuccessListener { doc ->
        if (doc.exists() && doc.getString("usernameLower") == null) {
            userRef.update("usernameLower", usernameKey(username))
                .addOnFailureListener { e -> android.util.Log.e("DuudleDebug", "usernameLower backfill failed: ${e.message}") }
        }
    }
}

fun saveFcmToken(uid: String, token: String) {
    FirebaseFirestore.getInstance().collection("users").document(uid)
        .update("fcmToken", token)
        .addOnFailureListener { e ->
            android.util.Log.e("DuudleFCM", "Failed to save FCM token: ${e.message}")
        }
}

class OverlayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        withContext(Dispatchers.Main) {
            showTestOverlay(applicationContext)
        }
        return Result.success()
    }
}

@Composable
fun OverlayTestScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryUnrestricted by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        )
    }
    var showBatteryGuide by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = Settings.canDrawOverlays(context)
                isBatteryUnrestricted = (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back") }

        Text(
            text = if (hasPermission) "✅ Overlay permission granted" else "❌ Overlay permission NOT granted",
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }) {
            Text("Grant Overlay Permission")
        }

        Text(
            text = if (isBatteryUnrestricted) "✅ Battery unrestricted" else "❌ Battery optimization may block background delivery",
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }) {
            Text("Allow Background Activity")
        }
        TextButton(onClick = { showBatteryGuide = true }) {
            Text("Button not working? Show manual steps", color = androidx.compose.ui.graphics.Color.DarkGray)
        }

        Button(
            onClick = { showTestOverlay(context) },
            enabled = hasPermission
        ) {
            Text("Show Test Overlay")
        }

        Button(onClick = {
            val request = OneTimeWorkRequestBuilder<OverlayWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }) {
            Text("Simulate Push in 30s")
        }
    }

    if (showBatteryGuide) {
        AlertDialog(
            onDismissRequest = { showBatteryGuide = false },
            title = { Text("Manual Battery Setup") },
            text = {
                Column {
                    Text("If the button above didn't show a popup, your phone manages battery settings its own way. Here's how to do it manually:")
                    Spacer(Modifier.height(8.dp))
                    Text("1. Open your phone's Settings app")
                    Text("2. Go to Apps")
                    Text("3. Find and tap Duudle")
                    Text("4. Tap Battery")
                    Text("5. Choose 'Unrestricted' (Samsung), 'No restrictions' (Xiaomi), or the least-restrictive option available")
                    Spacer(Modifier.height(8.dp))
                    Text("Come back here afterward — the status above updates automatically.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatteryGuide = false }) { Text("Got it") }
            }
        )
    }
}

private var overlayView: View? = null

fun showTestOverlay(context: Context) {
    if (overlayView != null) return
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#D8C7F5"))
        setPadding(32, 32, 32, 32)
    }
    layout.addView(TextView(context).apply {
        text = "🖍 Doodle from a friend"
        setTextColor(Color.BLACK)
        textSize = 16f
    })
    val buttonRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("Snooze", "Remove", "Draw").forEach { label ->
        buttonRow.addView(Button(context).apply {
            text = label
            setOnClickListener { removeTestOverlay(context) }
        })
    }
    layout.addView(buttonRow)

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
    }

    windowManager.addView(layout, params)
    overlayView = layout
}

fun removeTestOverlay(context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    overlayView?.let { windowManager.removeView(it); overlayView = null }
}

// ===== REAL DOODLE OVERLAY =====

private class DoodleCanvasView(context: Context, private val strokes: List<DoodleStroke>) : View(context) {
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (stroke in strokes) {
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.width
            val pts = stroke.points
            for (i in 0 until pts.size - 1) {
                canvas.drawLine(
                    pts[i].x * w, pts[i].y * h,
                    pts[i + 1].x * w, pts[i + 1].y * h,
                    paint
                )
            }
        }
    }
}

/** The overlay's square frame for a flash card: the image, or a note if this build lacks the card. */
private fun flashCardOverlayView(context: Context, cardId: String): View {
    val card = flashCardById(cardId)
    return if (card != null) {
        ImageView(context).apply {
            setImageResource(card.res)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.WHITE)
            contentDescription = card.label
        }
    } else {
        TextView(context).apply {
            // The title line above already names the sender.
            text = unknownFlashCardText(null)
            setTextColor(Color.DKGRAY)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
        }
    }
}

private var realDoodleOverlayView: View? = null

private fun dpToPx(context: Context, dp: Int): Int {
    val density = context.resources.displayMetrics.density
    return (dp * density).toInt()
}

fun showRealDoodleOverlay(
    context: Context,
    doodleId: String,
    senderUid: String,
    senderUsername: String,
    strokes: List<DoodleStroke>,
    // Set for a flash card: the card image replaces the stroke canvas (see FlashCards.kt).
    cardId: String? = null
) {
    removeRealDoodleOverlay(context)
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#F5F0FB"))
        setPadding(32, 32, 32, 32)
    }

    container.addView(TextView(context).apply {
        text = if (cardId != null) "🖍 ${flashCardSentLine(senderUsername)}" else "🖍 Doodle from $senderUsername"
        setTextColor(Color.BLACK)
        textSize = 16f
        setPadding(0, 0, 0, 16)
    })

    val canvasView = if (cardId != null) flashCardOverlayView(context, cardId) else DoodleCanvasView(context, strokes).apply {
        setBackgroundColor(Color.WHITE)
    }
    val canvasSize = dpToPx(context, 260)
    container.addView(canvasView, LinearLayout.LayoutParams(canvasSize, canvasSize))

    val buttonRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 16, 0, 0)
    }

    // Tapping Snooze swaps the main buttons for duration choices, with Back
    // on its own line since four buttons don't fit the overlay's width.
    val snoozePanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 16, 0, 0)
        visibility = View.GONE
    }
    val durationRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    for ((label, minutes) in listOf("5 min" to 5, "15 min" to 15, "1 hour" to 60)) {
        durationRow.addView(Button(context).apply {
            text = label
            setOnClickListener {
                DoodleSnooze.snooze(context, doodleId, senderUid, senderUsername, strokes, minutes, cardId)
                removeRealDoodleOverlay(context)
                Toast.makeText(context, "Snoozed for $label", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    snoozePanel.addView(durationRow)
    snoozePanel.addView(Button(context).apply {
        text = "Back"
        setOnClickListener {
            snoozePanel.visibility = View.GONE
            buttonRow.visibility = View.VISIBLE
        }
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    buttonRow.addView(Button(context).apply {
        text = "Snooze"
        setOnClickListener {
            buttonRow.visibility = View.GONE
            snoozePanel.visibility = View.VISIBLE
        }
    })
    buttonRow.addView(Button(context).apply {
        text = "Remove"
        setOnClickListener {
            markDoodleStatus(doodleId, "removed")
            DoodleSnooze.cancel(context, doodleId)
            removeRealDoodleOverlay(context)
        }
    })
    buttonRow.addView(Button(context).apply {
        text = "Draw"
        setOnClickListener {
            markDoodleStatus(doodleId, "removed")
            DoodleSnooze.cancel(context, doodleId)
            removeRealDoodleOverlay(context)
            val replyIntent = Intent(context, MainActivity::class.java).apply {
                // CLEAR_TOP + SINGLE_TOP route the intent to the running
                // MainActivity's onNewIntent instead of being silently dropped.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("reply_to_uid", senderUid)
                putExtra("reply_to_username", senderUsername)
            }
            context.startActivity(replyIntent)
        }
    })
    container.addView(buttonRow)
    container.addView(snoozePanel)

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    windowManager.addView(container, params)
    realDoodleOverlayView = container
}

fun removeRealDoodleOverlay(context: Context) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    realDoodleOverlayView?.let { windowManager.removeView(it); realDoodleOverlayView = null }
}