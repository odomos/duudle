package com.example.duudle

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.duudle.ui.theme.DuudleTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class Screen { WELCOME, HOME, DRAW, FRIENDS, OVERLAY_DEBUG, VIEW_DOODLE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuudleTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE) }
                var currentScreen by remember {
                    mutableStateOf(if (!prefs.getString("username", null).isNullOrBlank()) Screen.HOME else Screen.WELCOME)
                }
                var isSigningIn by remember { mutableStateOf(false) }
                var signInError by remember { mutableStateOf<String?>(null) }
                var recipientUid by remember { mutableStateOf<String?>(null) }
                var recipientUsername by remember { mutableStateOf<String?>(null) }
                var incomingDoodles by remember { mutableStateOf<List<IncomingDoodle>>(emptyList()) }

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val currentUsername = prefs.getString("username", "") ?: ""

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
                            newDoodleFromUsername = incomingDoodles.firstOrNull()?.senderUsername,
                            onViewDoodle = { currentScreen = Screen.VIEW_DOODLE }
                        )
                        Screen.DRAW -> DrawScreen(
                            modifier = Modifier.padding(innerPadding),
                            recipientUid = recipientUid,
                            recipientUsername = recipientUsername,
                            currentUid = currentUid,
                            currentUsername = currentUsername,
                            onBack = { currentScreen = Screen.HOME }
                        )
                        Screen.FRIENDS -> FriendsScreen(
                            modifier = Modifier.padding(innerPadding),
                            currentUid = currentUid,
                            currentUsername = currentUsername,
                            onBack = { currentScreen = Screen.HOME },
                            onSelectFriend = { friend ->
                                recipientUid = friend.uid
                                recipientUsername = friend.username
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
                                    onSnooze = { currentScreen = Screen.HOME },
                                    onRemove = {
                                        markDoodleStatus(doodle.docId, "removed")
                                        currentScreen = Screen.HOME
                                    },
                                    onReply = { senderUid, senderUsername ->
                                        markDoodleStatus(doodle.docId, "removed")
                                        recipientUid = senderUid
                                        recipientUsername = senderUsername
                                        currentScreen = Screen.DRAW
                                    }
                                )
                            }
                        }
                        Screen.OVERLAY_DEBUG -> OverlayTestScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
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

private fun saveUsername(uid: String, username: String, onResult: (Boolean, String?) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val userData = hashMapOf("username" to username, "uid" to uid)
    db.collection("users").document(uid).set(userData)
        .addOnSuccessListener { onResult(true, null) }
        .addOnFailureListener { e -> onResult(false, e.message) }
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